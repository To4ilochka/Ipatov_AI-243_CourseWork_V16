package ua.op.edu.ipatov.volunteer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.op.edu.ipatov.volunteer.config.RecommendationProperties;
import ua.op.edu.ipatov.volunteer.domain.*;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.RecommendationExplanationDto;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.RecommendedTaskDto;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.TaskCardDto;
import ua.op.edu.ipatov.volunteer.repository.TaskApplicationRepository;
import ua.op.edu.ipatov.volunteer.repository.UserAccountRepository;
import ua.op.edu.ipatov.volunteer.repository.VolunteerTaskRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Модульні тести багатокритеріального алгоритму рекомендацій завдань за навичками (погоджене ускладнення).
 */
@ExtendWith(MockitoExtension.class)
class SkillMatchRecommendationServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private VolunteerTaskRepository taskRepository;
    @Mock
    private TaskApplicationRepository applicationRepository;
    @Mock
    private CatalogAndInitiativeService catalogService;

    private SkillMatchRecommendationService service;
    private Skill skillWcag;
    private Skill skillPython;
    private UserAccount coordinator;
    private Initiative odesaInitiative;
    private Initiative lvivInitiative;

    @BeforeEach
    void setUp() {
        RecommendationProperties props = new RecommendationProperties(0.70, 0.20, 0.10, 0.35);
        service = new SkillMatchRecommendationService(
                props, userAccountRepository, taskRepository, applicationRepository, catalogService);

        skillWcag = new Skill("WEB_ACCESS", "Вебдоступність WCAG", "ІТ");
        setEntityId(skillWcag, 10L);
        skillPython = new Skill("DATA_CLEAN", "Впорядкування даних", "ІТ");
        setEntityId(skillPython, 20L);

        coordinator = new UserAccount("coord@test.ua", "hash", "Координатор", UserRole.COORDINATOR,
                "Одеса", "+380501112233", "@coord", null);
        setEntityId(coordinator, 1L);

        odesaInitiative = new Initiative(coordinator, "Цифрова Одеса", "ІТ", "Одеса",
                "Навчальна ініціатива цифрової доступності", InitiativeStatus.ACTIVE,
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(30));
        setEntityId(odesaInitiative, 100L);

        lvivInitiative = new Initiative(coordinator, "Еко Львів", "Екологія", "Львів",
                "Навчальна ініціатива озеленення парків", InitiativeStatus.ACTIVE,
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(30));
        setEntityId(lvivInitiative, 101L);
    }

    @Test
    @DisplayName("Повний збіг навичок, міста та критичної терміновості дає 100.0 балів і прозоре пояснення")
    void fullSkillAndCityAndCriticalUrgencyProducesMaxScore() {
        UserAccount vol = new UserAccount("v@test.ua", "hash", "Волонтер", UserRole.VOLUNTEER,
                "Одеса", "+380931112233", null, null);
        vol.getSkills().add(new VolunteerSkill(vol, skillWcag, (short) 3, null));
        vol.getSkills().add(new VolunteerSkill(vol, skillPython, (short) 2, null));

        VolunteerTask task = new VolunteerTask(odesaInitiative, "Аудит WCAG", "Опис робіт достатньої довжини",
                "Одеса", WorkFormat.ONSITE, TaskUrgency.CRITICAL, 3, new BigDecimal("5.0"),
                Instant.now().plus(7, ChronoUnit.DAYS));
        task.getRequiredSkills().add(new TaskRequiredSkill(task, skillWcag, (short) 2, true, (short) 4));
        task.getRequiredSkills().add(new TaskRequiredSkill(task, skillPython, (short) 2, false, (short) 2));

        RecommendationExplanationDto res = service.evaluateTaskForVolunteer(vol, task);

        assertThat(res.totalScore()).isEqualTo(100.0);
        assertThat(res.missingMandatorySkill()).isFalse();
        assertThat(res.matchedSkills()).hasSize(2);
        assertThat(res.missingOrInsufficientSkills()).isEmpty();
        assertThat(res.summaryText()).contains("100.0/100");
    }

    @Test
    @DisplayName("Відсутність обов'язкової навички застосовує штрафний множник 0.35 та позначає причину")
    void missingMandatorySkillAppliesPenaltyMultiplier() {
        UserAccount vol = new UserAccount("v@test.ua", "hash", "Волонтер", UserRole.VOLUNTEER,
                "Одеса", "+380931112233", null, null);
        vol.getSkills().add(new VolunteerSkill(vol, skillPython, (short) 2, null));

        VolunteerTask task = new VolunteerTask(odesaInitiative, "Аудит порталу", "Опис робіт достатньої довжини",
                "Дистанційно", WorkFormat.REMOTE, TaskUrgency.HIGH, 2, new BigDecimal("4.0"),
                Instant.now().plus(5, ChronoUnit.DAYS));
        task.getRequiredSkills().add(new TaskRequiredSkill(task, skillWcag, (short) 2, true, (short) 3));
        task.getRequiredSkills().add(new TaskRequiredSkill(task, skillPython, (short) 2, false, (short) 1));

        RecommendationExplanationDto res = service.evaluateTaskForVolunteer(vol, task);

        assertThat(res.missingMandatorySkill()).isTrue();
        assertThat(res.totalScore()).isLessThan(35.0);
        assertThat(res.summaryText()).contains("бракує обов'язкової навички");
    }

    @Test
    @DisplayName("Частковий рівень володіння (1 із 3) та очне завдання в іншому місті коректно знижують оцінку")
    void partialSkillLevelAndDifferentCityOnsiteCalculatedProportionally() {
        UserAccount vol = new UserAccount("v@test.ua", "hash", "Волонтер", UserRole.VOLUNTEER,
                "Одеса", "+380931112233", null, null);
        vol.getSkills().add(new VolunteerSkill(vol, skillWcag, (short) 1, null));

        VolunteerTask task = new VolunteerTask(lvivInitiative, "Очний семінар", "Опис семінару достатньої довжини",
                "Львів", WorkFormat.ONSITE, TaskUrgency.NORMAL, 4, new BigDecimal("3.0"),
                Instant.now().plus(10, ChronoUnit.DAYS));
        task.getRequiredSkills().add(new TaskRequiredSkill(task, skillWcag, (short) 3, true, (short) 5));

        RecommendationExplanationDto res = service.evaluateTaskForVolunteer(vol, task);

        assertThat(res.missingMandatorySkill()).isFalse();
        assertThat(res.skillComponentPoints()).isBetween(23.0, 24.0);
        assertThat(res.locationComponentPoints()).isEqualTo(7.0);
        assertThat(res.urgencyComponentPoints()).isEqualTo(4.0);
        assertThat(res.missingOrInsufficientSkills()).anyMatch(s -> s.contains("частково"));
    }

    @Test
    @DisplayName("Ранжування виключає завдання з уже поданою активною заявкою та сортує за спаданням індексу")
    void recommendTasksExcludesAlreadyAppliedAndSortsDescending() {
        UserAccount vol = new UserAccount("v@test.ua", "hash", "Волонтер", UserRole.VOLUNTEER,
                "Одеса", "+380931112233", null, null);
        setEntityId(vol, 5L);
        vol.getSkills().add(new VolunteerSkill(vol, skillWcag, (short) 3, null));

        VolunteerTask tApplied = new VolunteerTask(odesaInitiative, "Вже подана", "Опис", "Одеса",
                WorkFormat.REMOTE, TaskUrgency.CRITICAL, 2, new BigDecimal("2.0"), Instant.now().plus(3, ChronoUnit.DAYS));
        setEntityId(tApplied, 201L);

        VolunteerTask tBest = new VolunteerTask(odesaInitiative, "Ідеальний збіг", "Опис", "Одеса",
                WorkFormat.REMOTE, TaskUrgency.HIGH, 2, new BigDecimal("4.0"), Instant.now().plus(5, ChronoUnit.DAYS));
        setEntityId(tBest, 202L);
        tBest.getRequiredSkills().add(new TaskRequiredSkill(tBest, skillWcag, (short) 2, true, (short) 5));

        VolunteerTask tWeak = new VolunteerTask(lvivInitiative, "Інше місто без навичок", "Опис", "Львів",
                WorkFormat.ONSITE, TaskUrgency.NORMAL, 2, new BigDecimal("4.0"), Instant.now().plus(8, ChronoUnit.DAYS));
        setEntityId(tWeak, 203L);
        tWeak.getRequiredSkills().add(new TaskRequiredSkill(tWeak, skillPython, (short) 2, true, (short) 5));

        TaskApplication existingApp = new TaskApplication(tApplied, vol, "Вже подано мотивацію", new BigDecimal("95.0"));

        when(userAccountRepository.findWithSkillsById(5L)).thenReturn(Optional.of(vol));
        when(applicationRepository.findByVolunteerIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(existingApp));
        when(taskRepository.findAllCandidateTasksForMatching(any(), any())).thenReturn(List.of(tApplied, tWeak, tBest));
        when(catalogService.toTaskCardDto(any())).thenAnswer(inv -> {
            VolunteerTask t = inv.getArgument(0);
            return new TaskCardDto(t.getId(), 100L, "Ініціатива", "ІТ", "Одеса", "Коорд",
                    t.getTitle(), t.getDescription(), t.getLocationText(), t.getWorkFormat(),
                    t.getUrgency(), t.getMaxVolunteers(), t.getApprovedCount(), t.getRemainingSeats(),
                    t.getEstimatedHours(), t.getDeadlineAt(), t.getStatus(), List.of());
        });

        List<RecommendedTaskDto> recs = service.recommendTasksForVolunteer(5L, 10);

        assertThat(recs).hasSize(2);
        assertThat(recs.get(0).task().id()).isEqualTo(202L);
        assertThat(recs.get(1).task().id()).isEqualTo(203L);
        assertThat(recs.get(0).explanation().totalScore()).isGreaterThan(recs.get(1).explanation().totalScore());
    }

    private static void setEntityId(Object entity, Long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
