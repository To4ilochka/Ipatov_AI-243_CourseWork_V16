package ua.op.edu.ipatov.volunteer.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.op.edu.ipatov.volunteer.domain.*;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.*;
import ua.op.edu.ipatov.volunteer.exception.AccessPolicyViolationException;
import ua.op.edu.ipatov.volunteer.exception.DomainConflictException;
import ua.op.edu.ipatov.volunteer.exception.ResourceNotFoundException;
import ua.op.edu.ipatov.volunteer.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Сервіс координації наскрізного бізнес-процесу: подання заявки волонтером,
 * атомарне погодження/відхилення координатором та верифікація підсумкового звіту.
 */
@Service
public class WorkflowCoordinationService {

    private static final List<ApplicationStatus> ACTIVE_STATUSES = List.of(
            ApplicationStatus.PENDING, ApplicationStatus.APPROVED);

    private final UserAccountRepository userRepository;
    private final VolunteerTaskRepository taskRepository;
    private final TaskApplicationRepository applicationRepository;
    private final ExecutionReportRepository reportRepository;
    private final AuditEventRepository auditRepository;
    private final SkillMatchRecommendationService recommendationService;
    private final ContactVisibilityPolicy contactVisibilityPolicy;

    @Autowired(required = false)
    private EntityManager entityManager;

    public WorkflowCoordinationService(UserAccountRepository userRepository,
                                       VolunteerTaskRepository taskRepository,
                                       TaskApplicationRepository applicationRepository,
                                       ExecutionReportRepository reportRepository,
                                       AuditEventRepository auditRepository,
                                       SkillMatchRecommendationService recommendationService,
                                       ContactVisibilityPolicy contactVisibilityPolicy) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.applicationRepository = applicationRepository;
        this.reportRepository = reportRepository;
        this.auditRepository = auditRepository;
        this.recommendationService = recommendationService;
        this.contactVisibilityPolicy = contactVisibilityPolicy;
    }

    /**
     * Атомарне подання заявки волонтером із песимістичним блокуванням рядка завдання
     * та захистом від дублювання активної участі на рівні сервісу і часткового унікального індексу СКБД.
     */
    @Transactional
    public ApplicationDto submitApplication(Long volunteerId, SubmitApplicationRequest req) {
        UserAccount volunteer = userRepository.findWithSkillsById(volunteerId)
                .orElseThrow(() -> new ResourceNotFoundException("Обліковий запис волонтера не знайдено"));
        if (volunteer.getRole() != UserRole.VOLUNTEER) {
            throw new AccessPolicyViolationException("Подавати заявки на виконання завдань можуть лише волонтери");
        }

        VolunteerTask task = taskRepository.findByIdForUpdate(req.taskId())
                .orElseThrow(() -> new ResourceNotFoundException("Завдання з ID " + req.taskId() + " не знайдено"));

        Instant now = Instant.now();
        validateTaskAcceptingApplications(task, now);

        if (applicationRepository.existsByTaskIdAndVolunteerIdAndStatusIn(task.getId(), volunteerId, ACTIVE_STATUSES)) {
            throw new DomainConflictException("DUPLICATE_ACTIVE_APPLICATION",
                    "Ви вже подали активну заявку на це завдання");
        }

        // Ініціалізуємо навички завдання для розрахунку індексу відповідності
        task.getRequiredSkills().size();
        RecommendationExplanationDto explanation = recommendationService.evaluateTaskForVolunteer(volunteer, task);
        BigDecimal matchScore = BigDecimal.valueOf(explanation.totalScore());

        TaskApplication application = new TaskApplication(task, volunteer, req.motivation().trim(), matchScore);
        try {
            TaskApplication saved = applicationRepository.saveAndFlush(application);
            auditRepository.save(new AuditEvent("APPLICATION", saved.getId(), volunteerId,
                    "APPLICATION_SUBMITTED", "Подано заявку на завдання #" + task.getId() + ", score=" + matchScore));
            return toApplicationDto(saved, volunteer);
        } catch (DataIntegrityViolationException ex) {
            throw new DomainConflictException("DUPLICATE_ACTIVE_APPLICATION",
                    "Активна заявка цього волонтера на обране завдання вже існує");
        }
    }

    /**
     * Погодження заявки координатором ініціативи з атомарним резервуванням місця у завданні.
     */
    @Transactional
    public ApplicationDto approveApplication(Long coordinatorId, Long applicationId, String decisionNote) {
        UserAccount coordinator = requireCoordinator(coordinatorId);
        TaskApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Заявку з ID " + applicationId + " не знайдено"));

        VolunteerTask lockedTask = taskRepository.findByIdForUpdate(application.getTask().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Завдання не знайдено"));
        if (entityManager != null && entityManager.contains(lockedTask)) {
            try {
                entityManager.refresh(lockedTask, LockModeType.PESSIMISTIC_WRITE);
            } catch (org.springframework.dao.OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException ex) {
                throw new DomainConflictException("CONCURRENT_MODIFICATION",
                        "Стан завдання одночасно змінено іншим запитом координатора");
            }
        }
        verifyCoordinatorOwnsInitiative(coordinatorId, lockedTask.getInitiative());

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new DomainConflictException("INVALID_APPLICATION_STATE",
                    "Заявку не можна погодити у поточному стані: " + application.getStatus().getLabelUk());
        }
        if (!lockedTask.getInitiative().isActiveForApplications()) {
            throw new DomainConflictException("INITIATIVE_NOT_ACTIVE",
                    "Неможливо призначити учасника у неактивній або завершеній ініціативі");
        }
        if (lockedTask.getStatus() == TaskStatus.COMPLETED || lockedTask.getStatus() == TaskStatus.CANCELLED) {
            throw new DomainConflictException("TASK_CLOSED",
                    "Завдання вже завершене або скасоване");
        }
        if (!lockedTask.hasAvailableSeat()) {
            throw new DomainConflictException("SEAT_LIMIT_REACHED",
                    "Усі місця для завдання «" + lockedTask.getTitle() + "» вже заповнені");
        }

        Instant now = Instant.now();
        lockedTask.incrementApprovedCount();
        application.approve(coordinator, decisionNote != null ? decisionNote.trim() : "Призначено до виконання", now);

        taskRepository.save(lockedTask);
        TaskApplication saved = applicationRepository.save(application);
        auditRepository.save(new AuditEvent("APPLICATION", saved.getId(), coordinatorId,
                "APPLICATION_APPROVED", "Погоджено участь волонтера #" + application.getVolunteer().getId()));
        return toApplicationDto(saved, coordinator);
    }

    @Transactional
    public ApplicationDto rejectApplication(Long coordinatorId, Long applicationId, String decisionNote) {
        UserAccount coordinator = requireCoordinator(coordinatorId);
        TaskApplication application = applicationRepository.findDetailedById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Заявку з ID " + applicationId + " не знайдено"));
        verifyCoordinatorOwnsInitiative(coordinatorId, application.getTask().getInitiative());

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new DomainConflictException("INVALID_APPLICATION_STATE",
                    "Відхилити можна лише заявку у стані очікування розгляду");
        }
        if (decisionNote == null || decisionNote.trim().length() < 5) {
            throw new DomainConflictException("DECISION_NOTE_REQUIRED",
                    "Причина відхилення обов'язкова (щонайменше 5 символів)");
        }

        application.reject(coordinator, decisionNote.trim(), Instant.now());
        TaskApplication saved = applicationRepository.save(application);
        auditRepository.save(new AuditEvent("APPLICATION", saved.getId(), coordinatorId,
                "APPLICATION_REJECTED", "Відхилено заявку: " + decisionNote.trim()));
        return toApplicationDto(saved, coordinator);
    }

    @Transactional
    public ApplicationDto cancelApplicationByVolunteer(Long volunteerId, Long applicationId) {
        UserAccount volunteer = userRepository.findById(volunteerId)
                .orElseThrow(() -> new ResourceNotFoundException("Волонтера не знайдено"));
        TaskApplication application = applicationRepository.findDetailedById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Заявку не знайдено"));

        if (!application.getVolunteer().getId().equals(volunteerId)) {
            throw new AccessPolicyViolationException("Волонтер може скасовувати лише власні заявки");
        }
        if (reportRepository.findByApplicationId(applicationId).isPresent()) {
            throw new DomainConflictException("REPORT_ALREADY_SUBMITTED",
                    "Неможливо скасувати заявку після подання звіту про виконання");
        }

        VolunteerTask lockedTask = taskRepository.findByIdForUpdate(application.getTask().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Завдання не знайдено"));

        boolean wasApproved = application.getStatus() == ApplicationStatus.APPROVED;
        try {
            application.cancelByVolunteer(Instant.now());
        } catch (IllegalStateException ex) {
            throw new DomainConflictException("INVALID_APPLICATION_STATE", ex.getMessage());
        }

        if (wasApproved) {
            lockedTask.decrementApprovedCount();
            taskRepository.save(lockedTask);
        }
        TaskApplication saved = applicationRepository.save(application);
        auditRepository.save(new AuditEvent("APPLICATION", saved.getId(), volunteerId,
                "APPLICATION_CANCELLED", "Скасовано заявку волонтером"));
        return toApplicationDto(saved, volunteer);
    }

    /**
     * Подання або оновлення підсумкового звіту про виконання завдання автором погодженої заявки.
     */
    @Transactional
    public ReportDto submitOrUpdateReport(Long volunteerId, SubmitReportRequest req) {
        UserAccount volunteer = userRepository.findById(volunteerId)
                .orElseThrow(() -> new ResourceNotFoundException("Волонтера не знайдено"));
        TaskApplication application = applicationRepository.findDetailedById(req.applicationId())
                .orElseThrow(() -> new ResourceNotFoundException("Заявку з ID " + req.applicationId() + " не знайдено"));

        if (!application.getVolunteer().getId().equals(volunteerId)) {
            throw new AccessPolicyViolationException(
                    "Заборонено подавати або змінювати звіт за чужою заявкою");
        }
        if (application.getStatus() != ApplicationStatus.APPROVED) {
            throw new DomainConflictException("APPLICATION_NOT_APPROVED",
                    "Звіт про виконання можна подати лише для погодженої координатором заявки");
        }

        Instant now = Instant.now();
        ExecutionReport existing = reportRepository.findByApplicationId(application.getId()).orElse(null);
        ExecutionReport saved;
        if (existing != null) {
            if (!existing.getAuthor().getId().equals(volunteerId)) {
                throw new AccessPolicyViolationException("Заборонено редагувати чужий звіт про виконання");
            }
            if (existing.getStatus() == ReportStatus.VERIFIED) {
                throw new DomainConflictException("REPORT_ALREADY_VERIFIED",
                        "Підтверджений координатором звіт не підлягає редагуванню");
            }
            existing.updateByAuthor(req.summary().trim(), req.hoursSpent(),
                    req.impactMetrics() != null ? req.impactMetrics().trim() : null, now);
            saved = reportRepository.save(existing);
            auditRepository.save(new AuditEvent("REPORT", saved.getId(), volunteerId,
                    "REPORT_UPDATED", "Оновлено звіт про виконання, годин: " + req.hoursSpent()));
        } else {
            ExecutionReport report = new ExecutionReport(
                    application,
                    volunteer,
                    req.summary().trim(),
                    req.hoursSpent(),
                    req.impactMetrics() != null ? req.impactMetrics().trim() : null
            );
            saved = reportRepository.save(report);
            auditRepository.save(new AuditEvent("REPORT", saved.getId(), volunteerId,
                    "REPORT_SUBMITTED", "Подано підсумковий звіт про виконання, годин: " + req.hoursSpent()));
        }
        return toReportDto(saved);
    }

    @Transactional
    public ReportDto updateExistingReportById(Long actorId, Long reportId, SubmitReportRequest req) {
        ExecutionReport report = reportRepository.findDetailedById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Звіт з ID " + reportId + " не знайдено"));
        if (!report.getAuthor().getId().equals(actorId)) {
            throw new AccessPolicyViolationException("Заборонено змінювати чужий звіт про виконання");
        }
        if (report.getStatus() == ReportStatus.VERIFIED) {
            throw new DomainConflictException("REPORT_ALREADY_VERIFIED",
                    "Підтверджений координатором звіт закритий для редагування");
        }
        report.updateByAuthor(req.summary().trim(), req.hoursSpent(),
                req.impactMetrics() != null ? req.impactMetrics().trim() : null, Instant.now());
        ExecutionReport saved = reportRepository.save(report);
        auditRepository.save(new AuditEvent("REPORT", saved.getId(), actorId,
                "REPORT_UPDATED", "Відредаговано звіт #" + reportId));
        return toReportDto(saved);
    }

    @Transactional
    public ReportDto reviewReportByCoordinator(Long coordinatorId, Long reportId, VerifyReportRequest req) {
        requireCoordinator(coordinatorId);
        ExecutionReport report = reportRepository.findDetailedById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Звіт з ID " + reportId + " не знайдено"));
        verifyCoordinatorOwnsInitiative(coordinatorId, report.getApplication().getTask().getInitiative());

        Instant now = Instant.now();
        if (req.targetStatus() == ReportStatus.VERIFIED) {
            report.verify(req.feedback().trim(), now);
            VolunteerTask task = report.getApplication().getTask();
            if (task.getApprovedCount() >= task.getMaxVolunteers() && task.getStatus() == TaskStatus.IN_PROGRESS) {
                task.setStatus(TaskStatus.COMPLETED);
                taskRepository.save(task);
            }
        } else if (req.targetStatus() == ReportStatus.NEEDS_REVISION) {
            report.requestRevision(req.feedback().trim(), now);
        } else {
            throw new DomainConflictException("INVALID_REPORT_TRANSITION",
                    "Оберіть рішення: підтвердити звіт або повернути на уточнення");
        }

        ExecutionReport saved = reportRepository.save(report);
        auditRepository.save(new AuditEvent("REPORT", saved.getId(), coordinatorId,
                "REPORT_" + req.targetStatus().name(), "Перевірено звіт #" + reportId));
        return toReportDto(saved);
    }

    @Transactional(readOnly = true)
    public ParticipantContactDto getApplicationContacts(Long requesterId, Long applicationId) {
        UserAccount requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Користувача не знайдено"));
        TaskApplication application = applicationRepository.findDetailedById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Заявку з ID " + applicationId + " не знайдено"));
        return contactVisibilityPolicy.requireAuthorizedContacts(requester, application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> listVolunteerApplications(Long volunteerId) {
        UserAccount volunteer = userRepository.findById(volunteerId)
                .orElseThrow(() -> new ResourceNotFoundException("Волонтера не знайдено"));
        return applicationRepository.findByVolunteerIdOrderByCreatedAtDesc(volunteerId).stream()
                .map(a -> toApplicationDto(a, volunteer))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> listCoordinatorApplications(Long coordinatorId) {
        UserAccount coordinator = requireCoordinator(coordinatorId);
        return applicationRepository.findAllForCoordinator(coordinatorId).stream()
                .map(a -> toApplicationDto(a, coordinator))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDto> listVolunteerReports(Long volunteerId) {
        return reportRepository.findByAuthorIdOrderBySubmittedAtDesc(volunteerId).stream()
                .map(this::toReportDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDto> listCoordinatorReports(Long coordinatorId) {
        requireCoordinator(coordinatorId);
        return reportRepository.findAllForCoordinator(coordinatorId).stream()
                .map(this::toReportDto)
                .toList();
    }

    private void validateTaskAcceptingApplications(VolunteerTask task, Instant now) {
        if (!task.getInitiative().isActiveForApplications()) {
            throw new DomainConflictException("INITIATIVE_NOT_ACTIVE",
                    "Ініціатива «" + task.getInitiative().getTitle() + "» наразі не приймає нових заявок");
        }
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            throw new DomainConflictException("TASK_CLOSED",
                    "Завдання «" + task.getTitle() + "» вже завершене або скасоване");
        }
        if (!task.hasAvailableSeat() || task.getStatus() == TaskStatus.IN_PROGRESS) {
            throw new DomainConflictException("SEAT_LIMIT_REACHED",
                    "Ліміт учасників для цього завдання вичерпано");
        }
        if (!task.getDeadlineAt().isAfter(now)) {
            throw new DomainConflictException("DEADLINE_EXPIRED",
                    "Строк реєстрації на завдання вже завершився");
        }
    }

    private UserAccount requireCoordinator(Long coordinatorId) {
        UserAccount user = userRepository.findById(coordinatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Координатора не знайдено"));
        if (user.getRole() != UserRole.COORDINATOR) {
            throw new AccessPolicyViolationException("Операція дозволена лише користувачам із роллю координатора");
        }
        return user;
    }

    private void verifyCoordinatorOwnsInitiative(Long coordinatorId, Initiative initiative) {
        if (!initiative.getCoordinator().getId().equals(coordinatorId)) {
            throw new AccessPolicyViolationException(
                    "Координатор може керувати заявками і звітами лише власних ініціатив");
        }
    }

    public ApplicationDto toApplicationDto(TaskApplication app, UserAccount viewer) {
        return new ApplicationDto(
                app.getId(),
                app.getTask().getId(),
                app.getTask().getTitle(),
                app.getTask().getInitiative().getTitle(),
                app.getVolunteer().getId(),
                app.getVolunteer().getFullName(),
                app.getMotivation(),
                app.getMatchScore(),
                app.getStatus(),
                app.getDecisionNote(),
                app.getCreatedAt(),
                app.getDecidedAt(),
                contactVisibilityPolicy.buildContactDto(viewer, app)
        );
    }

    public ReportDto toReportDto(ExecutionReport r) {
        return new ReportDto(
                r.getId(),
                r.getApplication().getId(),
                r.getApplication().getTask().getId(),
                r.getApplication().getTask().getTitle(),
                r.getAuthor().getId(),
                r.getAuthor().getFullName(),
                r.getSummary(),
                r.getHoursSpent(),
                r.getImpactMetrics(),
                r.getStatus(),
                r.getCoordinatorFeedback(),
                r.getSubmittedAt(),
                r.getVerifiedAt()
        );
    }
}
