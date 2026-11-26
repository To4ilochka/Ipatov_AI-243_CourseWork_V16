package ua.op.edu.ipatov.volunteer.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.op.edu.ipatov.volunteer.config.RecommendationProperties;
import ua.op.edu.ipatov.volunteer.domain.*;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.*;
import ua.op.edu.ipatov.volunteer.exception.ResourceNotFoundException;
import ua.op.edu.ipatov.volunteer.repository.TaskApplicationRepository;
import ua.op.edu.ipatov.volunteer.repository.UserAccountRepository;
import ua.op.edu.ipatov.volunteer.repository.VolunteerTaskRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Алгоритмічний сервіс багатокритеріального підбору завдань за навичками волонтера
 * із формуванням структурованого текстового пояснення рекомендації (погоджене ускладнення Варіанта 16).
 */
@Service
public class SkillMatchRecommendationService {

    private final RecommendationProperties properties;
    private final UserAccountRepository userAccountRepository;
    private final VolunteerTaskRepository taskRepository;
    private final TaskApplicationRepository applicationRepository;
    private final CatalogAndInitiativeService catalogService;

    public SkillMatchRecommendationService(RecommendationProperties properties,
                                           UserAccountRepository userAccountRepository,
                                           VolunteerTaskRepository taskRepository,
                                           TaskApplicationRepository applicationRepository,
                                           CatalogAndInitiativeService catalogService) {
        this.properties = properties;
        this.userAccountRepository = userAccountRepository;
        this.taskRepository = taskRepository;
        this.applicationRepository = applicationRepository;
        this.catalogService = catalogService;
    }

    /**
     * Обчислює оцінку відповідності S(v, t) у діапазоні [0; 100] та деталізацію причин рекомендації.
     * Часова складність для одного завдання з k вимогами при хеш-таблиці профілю: O(k), пам'ять: O(k).
     */
    public RecommendationExplanationDto evaluateTaskForVolunteer(UserAccount volunteer, VolunteerTask task) {
        Map<Long, Short> volunteerSkillLevels = new HashMap<>();
        for (VolunteerSkill vs : volunteer.getSkills()) {
            volunteerSkillLevels.put(vs.getSkill().getId(), vs.getProficiencyLevel());
        }
        return evaluateWithSkillMap(volunteer.getCity(), volunteerSkillLevels, task);
    }

    public RecommendationExplanationDto evaluateWithSkillMap(String volunteerCity,
                                                             Map<Long, Short> volunteerSkillLevels,
                                                             VolunteerTask task) {
        List<TaskRequiredSkill> reqs = task.getRequiredSkills();
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        boolean missingMandatory = false;

        double weightedAchieved = 0.0;
        double totalWeight = 0.0;

        if (reqs.isEmpty()) {
            weightedAchieved = 1.0;
            totalWeight = 1.0;
            matched.add("Спеціальні навички не вимагаються (доступно всім волонтерам)");
        } else {
            for (TaskRequiredSkill req : reqs) {
                double w = req.getWeight();
                totalWeight += w;
                short actualLevel = volunteerSkillLevels.getOrDefault(req.getSkill().getId(), (short) 0);
                short minLevel = req.getMinLevel();

                if (actualLevel == 0) {
                    if (req.isMandatory()) {
                        missingMandatory = true;
                        missing.add(req.getSkill().getTitle() + " (обов'язкова, мін. рівень " + minLevel + ")");
                    } else {
                        missing.add(req.getSkill().getTitle() + " (бажана, мін. рівень " + minLevel + ")");
                    }
                } else if (actualLevel >= minLevel) {
                    weightedAchieved += w * 1.0;
                    matched.add(req.getSkill().getTitle() + " (рівень " + actualLevel + "/" + minLevel + ")");
                } else {
                    double ratio = (double) actualLevel / (double) minLevel;
                    weightedAchieved += w * ratio;
                    missing.add(req.getSkill().getTitle() + " (частково: рівень " + actualLevel + " із " + minLevel + ")");
                }
            }
        }

        double skillCoverage = totalWeight > 0 ? (weightedAchieved / totalWeight) : 1.0;
        double locationFactor = computeLocationFactor(volunteerCity, task);
        String locationReason = buildLocationReason(volunteerCity, task);
        double urgencyFactor = task.getUrgency().getFactor();

        double weightSum = properties.alphaSkill() + properties.betaLocation() + properties.gammaUrgency();
        double normAlpha = properties.alphaSkill() / weightSum;
        double normBeta = properties.betaLocation() / weightSum;
        double normGamma = properties.gammaUrgency() / weightSum;

        double penalty = missingMandatory ? properties.missingMandatoryPenalty() : 1.0;

        double skillPts = round2(100.0 * normAlpha * skillCoverage * penalty);
        double locPts = round2(100.0 * normBeta * locationFactor * penalty);
        double urgPts = round2(100.0 * normGamma * urgencyFactor * penalty);
        double totalScore = round2(Math.min(100.0, skillPts + locPts + urgPts));

        String summary = buildSummaryText(totalScore, matched, missing, missingMandatory, locationReason, task);

        return new RecommendationExplanationDto(
                totalScore,
                skillPts,
                locPts,
                urgPts,
                missingMandatory,
                matched,
                missing,
                locationReason,
                summary
        );
    }

    /**
     * Формує відсортований список рекомендованих відкритих завдань для волонтера (виключаючи вже подані).
     * Часова складність: O(m + n*k_avg + n*log n), де m — навички волонтера, n — активні завдання.
     */
    @Transactional(readOnly = true)
    public List<RecommendedTaskDto> recommendTasksForVolunteer(Long volunteerId, int limit) {
        UserAccount volunteer = userAccountRepository.findWithSkillsById(volunteerId)
                .orElseThrow(() -> new ResourceNotFoundException("Профіль волонтера не знайдено"));

        Map<Long, Short> skillMap = new HashMap<>();
        for (VolunteerSkill vs : volunteer.getSkills()) {
            skillMap.put(vs.getSkill().getId(), vs.getProficiencyLevel());
        }

        Set<Long> alreadyAppliedTaskIds = new HashSet<>();
        for (TaskApplication app : applicationRepository.findByVolunteerIdOrderByCreatedAtDesc(volunteerId)) {
            if (app.getStatus().isActive()) {
                alreadyAppliedTaskIds.add(app.getTask().getId());
            }
        }

        List<VolunteerTask> candidates = taskRepository.findAllCandidateTasksForMatching(TaskStatus.OPEN, Instant.now());
        List<RecommendedTaskDto> scored = new ArrayList<>();

        for (VolunteerTask task : candidates) {
            if (alreadyAppliedTaskIds.contains(task.getId())) {
                continue;
            }
            RecommendationExplanationDto explanation = evaluateWithSkillMap(volunteer.getCity(), skillMap, task);
            scored.add(new RecommendedTaskDto(catalogService.toTaskCardDto(task), explanation));
        }

        scored.sort(Comparator
                .comparingDouble((RecommendedTaskDto r) -> r.explanation().totalScore()).reversed()
                .thenComparing((RecommendedTaskDto r) -> r.task().urgency().getFactor(), Comparator.reverseOrder())
                .thenComparing(r -> r.task().deadlineAt())
                .thenComparing(r -> r.task().id()));

        return scored.size() > limit ? scored.subList(0, limit) : scored;
    }

    private double computeLocationFactor(String volunteerCity, VolunteerTask task) {
        if (task.getWorkFormat() == WorkFormat.REMOTE) {
            return 1.0;
        }
        String taskCity = task.getInitiative().getLocationCity();
        boolean sameCity = volunteerCity != null && taskCity != null
                && volunteerCity.trim().equalsIgnoreCase(taskCity.trim());
        if (sameCity) {
            return 1.0;
        }
        return task.getWorkFormat() == WorkFormat.HYBRID ? 0.65 : 0.35;
    }

    private String buildLocationReason(String volunteerCity, VolunteerTask task) {
        if (task.getWorkFormat() == WorkFormat.REMOTE) {
            return "Дистанційний формат — участь можлива з будь-якого міста";
        }
        String taskCity = task.getInitiative().getLocationCity();
        if (volunteerCity != null && volunteerCity.trim().equalsIgnoreCase(taskCity.trim())) {
            return "Місто завдання (" + taskCity + ") збігається з вашим профілем";
        }
        if (task.getWorkFormat() == WorkFormat.HYBRID) {
            return "Змішаний формат в іншому місті (" + taskCity + ")";
        }
        return "Очна присутність в іншому місті (" + taskCity + ")";
    }

    private String buildSummaryText(double score,
                                    List<String> matched,
                                    List<String> missing,
                                    boolean missingMandatory,
                                    String locationReason,
                                    VolunteerTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Індекс відповідності ").append(BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP)).append("/100. ");
        if (!matched.isEmpty()) {
            sb.append("Підтверджені навички: ").append(String.join(", ", matched)).append(". ");
        }
        if (missingMandatory) {
            sb.append("Увага: бракує обов'язкової навички, застосовано понижувальний коефіцієнт. ");
        } else if (!missing.isEmpty()) {
            sb.append("Можна підсилити: ").append(String.join(", ", missing)).append(". ");
        }
        sb.append(locationReason).append(". Вільних місць: ").append(task.getRemainingSeats()).append(".");
        return sb.toString();
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
