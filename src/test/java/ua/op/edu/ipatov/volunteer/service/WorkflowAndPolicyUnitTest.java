package ua.op.edu.ipatov.volunteer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.op.edu.ipatov.volunteer.domain.*;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.*;
import ua.op.edu.ipatov.volunteer.exception.AccessPolicyViolationException;
import ua.op.edu.ipatov.volunteer.exception.DomainConflictException;
import ua.op.edu.ipatov.volunteer.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Модульні тести предметних правил, політики видимості контактів та захисту від конфліктів у WorkflowCoordinationService.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowAndPolicyUnitTest {

    @Mock
    private UserAccountRepository userRepository;
    @Mock
    private VolunteerTaskRepository taskRepository;
    @Mock
    private TaskApplicationRepository applicationRepository;
    @Mock
    private ExecutionReportRepository reportRepository;
    @Mock
    private AuditEventRepository auditRepository;
    @Mock
    private SkillMatchRecommendationService recommendationService;

    private ContactVisibilityPolicy visibilityPolicy;
    private WorkflowCoordinationService workflowService;

    private UserAccount ownerCoord;
    private UserAccount foreignCoord;
    private UserAccount volA;
    private UserAccount volB;
    private Initiative activeInit;
    private VolunteerTask openTask;

    @BeforeEach
    void setUp() {
        visibilityPolicy = new ContactVisibilityPolicy();
        workflowService = new WorkflowCoordinationService(
                userRepository, taskRepository, applicationRepository,
                reportRepository, auditRepository, recommendationService, visibilityPolicy);

        ownerCoord = user(1L, "coord1@test.ua", UserRole.COORDINATOR);
        foreignCoord = user(2L, "coord2@test.ua", UserRole.COORDINATOR);
        volA = user(10L, "volA@test.ua", UserRole.VOLUNTEER);
        volB = user(11L, "volB@test.ua", UserRole.VOLUNTEER);

        activeInit = new Initiative(ownerCoord, "Активна ініціатива", "Екологія", "Одеса",
                "Опис активної ініціативи", InitiativeStatus.ACTIVE,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(20));
        setId(activeInit, 50L);

        openTask = new VolunteerTask(activeInit, "Прибирання парку", "Детальний опис завдання",
                "Одеса", WorkFormat.ONSITE, TaskUrgency.HIGH, 1, new BigDecimal("4.0"),
                Instant.now().plus(5, ChronoUnit.DAYS));
        setId(openTask, 70L);
    }

    @Test
    @DisplayName("Політика видимості контактів: дозволяє власнику-координатору та самому волонтеру, блокує сторонніх")
    void contactVisibilityPolicyEnforcesStrictAuthorization() {
        TaskApplication app = new TaskApplication(openTask, volA, "Мотивація довжиною понад 15 символів", new BigDecimal("90.0"));
        setId(app, 300L);

        assertThat(visibilityPolicy.requireAuthorizedContacts(ownerCoord, app).phone()).isEqualTo("+380501234567");
        assertThat(visibilityPolicy.requireAuthorizedContacts(volA, app).email()).isEqualTo("volA@test.ua");

        assertThatThrownBy(() -> visibilityPolicy.requireAuthorizedContacts(foreignCoord, app))
                .isInstanceOf(AccessPolicyViolationException.class);
        assertThatThrownBy(() -> visibilityPolicy.requireAuthorizedContacts(volB, app))
                .isInstanceOf(AccessPolicyViolationException.class);

        app.cancelByVolunteer(Instant.now());
        ParticipantContactDto masked = visibilityPolicy.buildContactDto(ownerCoord, app);
        assertThat(masked.contactsAuthorized()).isFalse();
        assertThat(masked.phone()).isNull();
    }

    @Test
    @DisplayName("Відмова при поданні заявки на завершене завдання або повторній активній заявці")
    void submitApplicationRejectsClosedTaskAndDuplicate() {
        when(userRepository.findWithSkillsById(10L)).thenReturn(Optional.of(volA));
        openTask.setStatus(TaskStatus.COMPLETED);
        when(taskRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(openTask));

        assertThatThrownBy(() -> workflowService.submitApplication(10L,
                new SubmitApplicationRequest(70L, "Хочу долучитися до завершеного завдання")))
                .isInstanceOf(DomainConflictException.class)
                .extracting("errorCode").isEqualTo("TASK_CLOSED");

        openTask.setStatus(TaskStatus.OPEN);
        when(applicationRepository.existsByTaskIdAndVolunteerIdAndStatusIn(eq(70L), eq(10L), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> workflowService.submitApplication(10L,
                new SubmitApplicationRequest(70L, "Повторна спроба подати заявку")))
                .isInstanceOf(DomainConflictException.class)
                .extracting("errorCode").isEqualTo("DUPLICATE_ACTIVE_APPLICATION");
    }

    @Test
    @DisplayName("Погодження заявки інкриментує лічильник місць, а при переповненні ліміту повертає SEAT_LIMIT_REACHED")
    void approveApplicationReservesSeatAndPreventsOverbooking() {
        TaskApplication app1 = new TaskApplication(openTask, volA, "Мотивація першого волонтера", new BigDecimal("88.0"));
        setId(app1, 401L);
        TaskApplication app2 = new TaskApplication(openTask, volB, "Мотивація другого волонтера", new BigDecimal("85.0"));
        setId(app2, 402L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(ownerCoord));
        when(taskRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(openTask));
        when(applicationRepository.findById(401L)).thenReturn(Optional.of(app1));
        when(applicationRepository.findById(402L)).thenReturn(Optional.of(app2));
        when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApplicationDto approved = workflowService.approveApplication(1L, 401L, "Погоджено на єдине місце");
        assertThat(approved.status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(openTask.getApprovedCount()).isEqualTo(1);
        assertThat(openTask.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        assertThatThrownBy(() -> workflowService.approveApplication(1L, 402L, "Спроба перевищити ліміт"))
                .isInstanceOf(DomainConflictException.class)
                .extracting("errorCode").isEqualTo("SEAT_LIMIT_REACHED");
    }

    @Test
    @DisplayName("Заборона редагування чужого звіту (403) та підтвердженого звіту (409)")
    void reportModificationBlocksForeignUserAndVerifiedState() {
        TaskApplication app = new TaskApplication(openTask, volA, "Мотивація достатньої довжини", new BigDecimal("92.0"));
        setId(app, 501L);
        app.approve(ownerCoord, "Ок", Instant.now());

        ExecutionReport report = new ExecutionReport(app, volA,
                "Виконано повний обсяг робіт згідно з описом завдання.", new BigDecimal("4.0"), "10 дерев");
        setId(report, 601L);

        when(reportRepository.findDetailedById(601L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> workflowService.updateExistingReportById(11L, 601L,
                new SubmitReportRequest(501L, "Спроба підмінити чужий текст звіту!", new BigDecimal("1.0"), null)))
                .isInstanceOf(AccessPolicyViolationException.class);

        report.verify("Прийнято", Instant.now());
        assertThatThrownBy(() -> workflowService.updateExistingReportById(10L, 601L,
                new SubmitReportRequest(501L, "Спроба змінити вже верифікований звіт.", new BigDecimal("5.0"), null)))
                .isInstanceOf(DomainConflictException.class)
                .extracting("errorCode").isEqualTo("REPORT_ALREADY_VERIFIED");
    }

    @Test
    @DisplayName("Скасування погодженої заявки волонтером атомарно звільняє місце у завданні")
    void cancellingApprovedApplicationReleasesSeat() {
        openTask.incrementApprovedCount();
        TaskApplication app = new TaskApplication(openTask, volA, "Мотивація волонтера", new BigDecimal("90.0"));
        setId(app, 701L);
        app.approve(ownerCoord, "Ок", Instant.now());

        when(userRepository.findById(10L)).thenReturn(Optional.of(volA));
        when(applicationRepository.findDetailedById(701L)).thenReturn(Optional.of(app));
        when(reportRepository.findByApplicationId(701L)).thenReturn(Optional.empty());
        when(taskRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(openTask));
        when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ApplicationDto cancelled = workflowService.cancelApplicationByVolunteer(10L, 701L);
        assertThat(cancelled.status()).isEqualTo(ApplicationStatus.CANCELLED);
        assertThat(openTask.getApprovedCount()).isZero();
        assertThat(openTask.getStatus()).isEqualTo(TaskStatus.OPEN);
    }

    private static UserAccount user(Long id, String email, UserRole role) {
        UserAccount u = new UserAccount(email, "hash", "Тестовий " + id, role,
                "Одеса", "+380501234567", "@tg" + id, null);
        setId(u, id);
        return u;
    }

    private static void setId(Object entity, Long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
