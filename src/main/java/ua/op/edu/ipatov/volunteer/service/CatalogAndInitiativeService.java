package ua.op.edu.ipatov.volunteer.service;

import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.op.edu.ipatov.volunteer.domain.*;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.*;
import ua.op.edu.ipatov.volunteer.exception.AccessPolicyViolationException;
import ua.op.edu.ipatov.volunteer.exception.DomainConflictException;
import ua.op.edu.ipatov.volunteer.exception.ResourceNotFoundException;
import ua.op.edu.ipatov.volunteer.repository.*;
import java.time.Instant;
import java.util.*;

/**
 * Прикладний сервіс каталогу завдань, керування ініціативами координатора та профілем навичок волонтера.
 */
@Service
public class CatalogAndInitiativeService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("deadlineAt", "createdAt", "urgency");

    private final UserAccountRepository userRepository;
    private final SkillRepository skillRepository;
    private final VolunteerSkillRepository volunteerSkillRepository;
    private final InitiativeRepository initiativeRepository;
    private final VolunteerTaskRepository taskRepository;
    private final AuditEventRepository auditRepository;
    private final PasswordEncoder passwordEncoder;

    public CatalogAndInitiativeService(UserAccountRepository userRepository,
                                       SkillRepository skillRepository,
                                       VolunteerSkillRepository volunteerSkillRepository,
                                       InitiativeRepository initiativeRepository,
                                       VolunteerTaskRepository taskRepository,
                                       AuditEventRepository auditRepository,
                                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.skillRepository = skillRepository;
        this.volunteerSkillRepository = volunteerSkillRepository;
        this.initiativeRepository = initiativeRepository;
        this.taskRepository = taskRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount registerVolunteer(RegisterVolunteerRequest req) {
        String normalizedEmail = req.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DomainConflictException("EMAIL_ALREADY_EXISTS",
                    "Обліковий запис із такою електронною поштою вже зареєстровано");
        }
        UserAccount account = new UserAccount(
                normalizedEmail,
                passwordEncoder.encode(req.password()),
                req.fullName().trim(),
                UserRole.VOLUNTEER,
                req.city().trim(),
                req.phone().trim(),
                req.telegramHandle() != null && !req.telegramHandle().isBlank() ? req.telegramHandle().trim() : null,
                req.bio() != null && !req.bio().isBlank() ? req.bio().trim() : null
        );
        UserAccount saved = userRepository.save(account);
        auditRepository.save(new AuditEvent("USER", saved.getId(), saved.getId(),
                "VOLUNTEER_REGISTERED", "Зареєстровано нового волонтера у місті " + saved.getCity()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<TaskCardDto> searchPublicTasks(String query,
                                               String category,
                                               String city,
                                               WorkFormat workFormat,
                                               TaskUrgency urgency,
                                               Long skillId,
                                               boolean onlyOpen,
                                               int page,
                                               int size,
                                               String sortBy) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        String safeSort = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "deadlineAt";
        Sort sort = Sort.by(Sort.Order.asc(safeSort), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<VolunteerTask> result = taskRepository.findAll(
                TaskSpecifications.publicCatalogFilter(query, category, city, workFormat, urgency, skillId, onlyOpen),
                pageable
        );
        return result.map(this::toTaskCardDto);
    }

    @Transactional(readOnly = true)
    public VolunteerTask getTaskDetailsForPublicOrOwner(Long taskId, UserAccount currentUser) {
        VolunteerTask task = taskRepository.findDetailedById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Завдання з ID " + taskId + " не знайдено"));
        InitiativeStatus initStatus = task.getInitiative().getStatus();
        if (initStatus == InitiativeStatus.DRAFT || initStatus == InitiativeStatus.ARCHIVED) {
            boolean isOwner = currentUser != null
                    && currentUser.getRole() == UserRole.COORDINATOR
                    && task.getInitiative().getCoordinator().getId().equals(currentUser.getId());
            if (!isOwner) {
                throw new AccessPolicyViolationException("Непублічна ініціатива доступна лише її координатору");
            }
        }
        return task;
    }

    @Transactional(readOnly = true)
    public List<Skill> listAllSkills() {
        return skillRepository.findAllByOrderByCategoryAscTitleAsc();
    }

    @Transactional(readOnly = true)
    public UserAccount getUserWithSkills(Long userId) {
        return userRepository.findWithSkillsById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Користувача не знайдено"));
    }

    @Transactional
    public void upsertVolunteerSkill(Long volunteerId, SkillSelectionRequest req) {
        UserAccount volunteer = userRepository.findWithSkillsById(volunteerId)
                .orElseThrow(() -> new ResourceNotFoundException("Волонтера не знайдено"));
        if (volunteer.getRole() != UserRole.VOLUNTEER) {
            throw new AccessPolicyViolationException("Лише волонтер може редагувати профіль навичок");
        }
        Skill skill = skillRepository.findById(req.skillId())
                .orElseThrow(() -> new ResourceNotFoundException("Навичку з ID " + req.skillId() + " не знайдено"));

        Optional<VolunteerSkill> existing = volunteer.getSkills().stream()
                .filter(vs -> vs.getSkill().getId().equals(skill.getId()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setProficiencyLevel(req.proficiencyLevel());
            existing.get().setNotes(req.notes());
        } else {
            volunteer.getSkills().add(new VolunteerSkill(volunteer, skill, req.proficiencyLevel(), req.notes()));
        }
        userRepository.save(volunteer);
    }

    @Transactional
    public void removeVolunteerSkill(Long volunteerId, Long skillId) {
        volunteerSkillRepository.deleteByVolunteerIdAndSkillId(volunteerId, skillId);
    }

    @Transactional
    public Initiative createInitiative(Long coordinatorId, CreateInitiativeRequest req) {
        UserAccount coordinator = userRepository.findById(coordinatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Координатора не знайдено"));
        if (coordinator.getRole() != UserRole.COORDINATOR) {
            throw new AccessPolicyViolationException("Створювати ініціативи має право лише координатор");
        }
        if (req.endsOn().isBefore(req.startsOn())) {
            throw new DomainConflictException("INVALID_DATE_RANGE",
                    "Дата завершення ініціативи не може бути раніше дати початку");
        }
        Initiative initiative = new Initiative(
                coordinator,
                req.title().trim(),
                req.category().trim(),
                req.locationCity().trim(),
                req.summary().trim(),
                req.status(),
                req.startsOn(),
                req.endsOn()
        );
        Initiative saved = initiativeRepository.save(initiative);
        auditRepository.save(new AuditEvent("INITIATIVE", saved.getId(), coordinatorId,
                "INITIATIVE_CREATED", "Створено ініціативу «" + saved.getTitle() + "» зі статусом " + saved.getStatus()));
        return saved;
    }

    @Transactional
    public Initiative updateInitiativeStatus(Long coordinatorId, Long initiativeId, InitiativeStatus newStatus) {
        Initiative initiative = initiativeRepository.findWithTasksById(initiativeId)
                .orElseThrow(() -> new ResourceNotFoundException("Ініціативу не знайдено"));
        if (!initiative.getCoordinator().getId().equals(coordinatorId)) {
            throw new AccessPolicyViolationException("Координатор може змінювати лише власні ініціативи");
        }
        initiative.setStatus(newStatus);
        Initiative saved = initiativeRepository.save(initiative);
        auditRepository.save(new AuditEvent("INITIATIVE", saved.getId(), coordinatorId,
                "INITIATIVE_STATUS_CHANGED", "Новий статус: " + newStatus));
        return saved;
    }

    @Transactional
    public VolunteerTask createTask(Long coordinatorId, CreateTaskRequest req) {
        Initiative initiative = initiativeRepository.findById(req.initiativeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ініціативу з ID " + req.initiativeId() + " не знайдено"));
        if (!initiative.getCoordinator().getId().equals(coordinatorId)) {
            throw new AccessPolicyViolationException("Додавати завдання можна лише до власних ініціатив");
        }
        if (initiative.getStatus() == InitiativeStatus.COMPLETED || initiative.getStatus() == InitiativeStatus.ARCHIVED) {
            throw new DomainConflictException("INITIATIVE_CLOSED",
                    "Неможливо додати нове завдання до завершеної або архівованої ініціативи");
        }
        if (!req.deadlineAt().isAfter(Instant.now())) {
            throw new DomainConflictException("PAST_DEADLINE",
                    "Кінцевий строк подання заявок має бути у майбутньому");
        }

        VolunteerTask task = new VolunteerTask(
                initiative,
                req.title().trim(),
                req.description().trim(),
                req.locationText().trim(),
                req.workFormat(),
                req.urgency(),
                req.maxVolunteers(),
                req.estimatedHours(),
                req.deadlineAt()
        );

        if (req.requiredSkills() != null) {
            Set<Long> addedSkills = new HashSet<>();
            for (TaskSkillRequirementInput input : req.requiredSkills()) {
                if (input.skillId() == null || !addedSkills.add(input.skillId())) {
                    continue;
                }
                Skill skill = skillRepository.findById(input.skillId())
                        .orElseThrow(() -> new ResourceNotFoundException("Навичку з ID " + input.skillId() + " не знайдено"));
                task.getRequiredSkills().add(new TaskRequiredSkill(
                        task, skill, input.minLevel(), input.mandatory(), input.weight()));
            }
        }

        VolunteerTask saved = taskRepository.save(task);
        auditRepository.save(new AuditEvent("TASK", saved.getId(), coordinatorId,
                "TASK_CREATED", "Створено завдання «" + saved.getTitle() + "», місць: " + saved.getMaxVolunteers()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Initiative> listCoordinatorInitiatives(Long coordinatorId) {
        return initiativeRepository.findByCoordinatorIdOrderByCreatedAtDesc(coordinatorId);
    }

    @Transactional(readOnly = true)
    public List<Initiative> listActiveInitiatives() {
        return initiativeRepository.findByStatusOrderByCreatedAtDesc(InitiativeStatus.ACTIVE);
    }

    public TaskCardDto toTaskCardDto(VolunteerTask task) {
        List<RequiredSkillDto> skillDtos = task.getRequiredSkills().stream()
                .map(rs -> new RequiredSkillDto(
                        rs.getSkill().getId(),
                        rs.getSkill().getCode(),
                        rs.getSkill().getTitle(),
                        rs.getMinLevel(),
                        rs.isMandatory(),
                        rs.getWeight()))
                .toList();

        return new TaskCardDto(
                task.getId(),
                task.getInitiative().getId(),
                task.getInitiative().getTitle(),
                task.getInitiative().getCategory(),
                task.getInitiative().getLocationCity(),
                task.getInitiative().getCoordinator().getFullName(),
                task.getTitle(),
                task.getDescription(),
                task.getLocationText(),
                task.getWorkFormat(),
                task.getUrgency(),
                task.getMaxVolunteers(),
                task.getApprovedCount(),
                task.getRemainingSeats(),
                task.getEstimatedHours(),
                task.getDeadlineAt(),
                task.getStatus(),
                skillDtos
        );
    }
}
