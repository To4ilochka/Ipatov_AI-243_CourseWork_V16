package ua.op.edu.ipatov.volunteer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ua.op.edu.ipatov.volunteer.domain.*;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.*;
import ua.op.edu.ipatov.volunteer.exception.DomainConflictException;
import ua.op.edu.ipatov.volunteer.repository.*;
import ua.op.edu.ipatov.volunteer.service.WorkflowCoordinationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Інтеграційні та функціональні тести повного циклу з реальною СКБД PostgreSQL у Docker і міграціями Flyway:
 * перевірка FR-01..FR-08, негативних сценаріїв валідації (400), прав доступу (401/403), конфліктів (409)
 * та конкурентного бронювання останнього місця у завданні.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformIntegrationTest {

    private static final String PASS = "TestPass#2026!Secure15";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private InitiativeRepository initiativeRepository;
    @Autowired private VolunteerTaskRepository taskRepository;
    @Autowired private TaskApplicationRepository applicationRepository;
    @Autowired private ExecutionReportRepository reportRepository;
    @Autowired private AuditEventRepository auditRepository;
    @Autowired private WorkflowCoordinationService workflowService;

    private UserAccount coord1;
    private UserAccount coord2;
    private UserAccount vol1;
    private UserAccount vol2;
    private Skill skillEco;
    private Initiative activeInit;
    private VolunteerTask singleSeatTask;
    private VolunteerTask completedTask;

    @BeforeEach
    void cleanAndSeedTestDb() {
        reportRepository.deleteAll();
        applicationRepository.deleteAll();
        taskRepository.deleteAll();
        initiativeRepository.deleteAll();
        auditRepository.deleteAll();
        userRepository.deleteAll();
        skillRepository.deleteAll();

        skillEco = skillRepository.save(new Skill("ECO_TEST", "Екологічний моніторинг", "Екологія"));
        String hash = passwordEncoder.encode(PASS);

        coord1 = userRepository.save(new UserAccount("c1@test.op.edu.ua", hash, "Координатор Перший",
                UserRole.COORDINATOR, "Одеса", "+380501110011", "@c1", null));
        coord2 = userRepository.save(new UserAccount("c2@test.op.edu.ua", hash, "Координатор Другий",
                UserRole.COORDINATOR, "Львів", "+380502220022", "@c2", null));

        vol1 = new UserAccount("v1@test.op.edu.ua", hash, "Волонтер Перший",
                UserRole.VOLUNTEER, "Одеса", "+380931112233", "@vol_one_secret", null);
        vol1.getSkills().add(new VolunteerSkill(vol1, skillEco, (short) 3, "Досвід"));
        vol1 = userRepository.save(vol1);

        vol2 = new UserAccount("v2@test.op.edu.ua", hash, "Волонтер Другий",
                UserRole.VOLUNTEER, "Одеса", "+380934445566", "@vol_two_secret", null);
        vol2.getSkills().add(new VolunteerSkill(vol2, skillEco, (short) 2, "Досвід"));
        vol2 = userRepository.save(vol2);

        activeInit = initiativeRepository.save(new Initiative(coord1, "Тестова ініціатива Одеси",
                "Екологія", "Одеса", "Публічний опис тестової волонтерської ініціативи достатньої довжини.",
                InitiativeStatus.ACTIVE, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));

        singleSeatTask = new VolunteerTask(activeInit, "Завдання з одним місцем",
                "Докладний опис завдання для інтеграційної перевірки всіх бізнес-правил.",
                "м. Одеса, парк", WorkFormat.ONSITE, TaskUrgency.CRITICAL, 1,
                new BigDecimal("4.0"), Instant.now().plus(7, ChronoUnit.DAYS));
        singleSeatTask.getRequiredSkills().add(new TaskRequiredSkill(singleSeatTask, skillEco, (short) 2, true, (short) 5));
        singleSeatTask = taskRepository.save(singleSeatTask);

        completedTask = new VolunteerTask(activeInit, "Вже завершене завдання",
                "Докладний опис завершеного завдання для негативного тестування.",
                "м. Одеса", WorkFormat.ONSITE, TaskUrgency.NORMAL, 2,
                new BigDecimal("3.0"), Instant.now().plus(2, ChronoUnit.DAYS));
        completedTask.setStatus(TaskStatus.COMPLETED);
        completedTask = taskRepository.save(completedTask);
    }

    @Test
    @DisplayName("Наскрізний позитивний сценарій: рекомендація -> заявка -> погодження -> контакти -> звіт -> верифікація")
    void endToEndVolunteerAndCoordinatorFlowSucceeds() throws Exception {
        mockMvc.perform(get("/api/public/tasks").param("city", "Одеса").param("onlyOpen", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Завдання з одним місцем"));

        mockMvc.perform(get("/api/volunteer/recommendations")
                        .with(httpBasic("v1@test.op.edu.ua", PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].explanation.totalScore").value(100.0));

        String appJson = objectMapper.writeValueAsString(new SubmitApplicationRequest(
                singleSeatTask.getId(), "Маю необхідний інвентар та підтверджену навичку екологічного моніторингу."));
        String createdBody = mockMvc.perform(post("/api/volunteer/applications")
                        .with(httpBasic("v1@test.op.edu.ua", PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Long appId = objectMapper.readTree(createdBody).get("id").asLong();

        mockMvc.perform(post("/api/volunteer/applications")
                        .with(httpBasic("v1@test.op.edu.ua", PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ACTIVE_APPLICATION"));

        mockMvc.perform(post("/api/coordinator/applications/{id}/approve", appId)
                        .with(httpBasic("c1@test.op.edu.ua", PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CoordinatorDecisionRequest("Призначено на завдання"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/applications/{id}/contacts", appId)
                        .with(httpBasic("c1@test.op.edu.ua", PASS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+380931112233"))
                .andExpect(jsonPath("$.telegramHandle").value("@vol_one_secret"));

        String repBody = mockMvc.perform(post("/api/volunteer/reports")
                        .with(httpBasic("v1@test.op.edu.ua", PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitReportRequest(
                                appId, "Виконано повний екологічний моніторинг ділянки та зібрано проби.",
                                new BigDecimal("4.5"), "Перевірено 3 сектори"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn().getResponse().getContentAsString();
        Long reportId = objectMapper.readTree(repBody).get("id").asLong();

        mockMvc.perform(post("/api/coordinator/reports/{id}/review", reportId)
                        .with(httpBasic("c1@test.op.edu.ua", PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyReportRequest(
                                ReportStatus.VERIFIED, "Звіт прийнято без зауважень"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));
    }

    @Test
    @DisplayName("Негативні перевірки безпеки й цілісності: завершене завдання (409), закриті контакти через API (401/403), чужий звіт (403), валідація (400)")
    void negativeSecurityAndValidationRulesEnforced() throws Exception {
        mockMvc.perform(post("/api/volunteer/applications")
                        .with(httpBasic("v1@test.op.edu.ua", PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitApplicationRequest(
                                completedTask.getId(), "Спроба подати заявку на вже закрите завдання"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_CLOSED"));

        mockMvc.perform(post("/api/volunteer/applications")
                        .with(httpBasic("v1@test.op.edu.ua", PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitApplicationRequest(
                                singleSeatTask.getId(), "Коротко"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.motivation").exists());

        ApplicationDto app = workflowService.submitApplication(vol1.getId(),
                new SubmitApplicationRequest(singleSeatTask.getId(), "Достатня мотивація для створення тестової заявки."));
        workflowService.approveApplication(coord1.getId(), app.id(), "Ок");
        ReportDto rep = workflowService.submitOrUpdateReport(vol1.getId(),
                new SubmitReportRequest(app.id(), "Початковий достовірний текст звіту першого волонтера.",
                        new BigDecimal("4.0"), "Показник 1"));

        mockMvc.perform(get("/api/applications/{id}/contacts", app.id()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/applications/{id}/contacts", app.id())
                        .with(httpBasic("v2@test.op.edu.ua", PASS)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").exists());

        mockMvc.perform(get("/api/applications/{id}/contacts", app.id())
                        .with(httpBasic("c2@test.op.edu.ua", PASS)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/volunteer/reports/{reportId}", rep.id())
                        .with(httpBasic("v2@test.op.edu.ua", PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitReportRequest(
                                app.id(), "Несанкціонована спроба перезаписати чужий звіт!",
                                new BigDecimal("1.0"), null))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("email", "newuser@test.op.edu.ua")
                        .param("password", "short123")
                        .param("fullName", "Новий Тестувальник")
                        .param("city", "Одеса")
                        .param("phone", "+380991112233"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Конкурентне погодження двох заявок на єдине вільне місце: рівно 1 успіх і 1 відмова 409 без перевищення ліміту")
    void concurrentApprovalForLastSeatAllowsExactlyOneWinner() throws Exception {
        ApplicationDto app1 = workflowService.submitApplication(vol1.getId(),
                new SubmitApplicationRequest(singleSeatTask.getId(), "Перший кандидат на єдине вільне місце у завданні."));
        ApplicationDto app2 = workflowService.submitApplication(vol2.getId(),
                new SubmitApplicationRequest(singleSeatTask.getId(), "Другий кандидат на єдине вільне місце у завданні."));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        List<Long> targetApps = List.of(app1.id(), app2.id());
        for (Long targetAppId : targetApps) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    workflowService.approveApplication(coord1.getId(), targetAppId, "Одночасне погодження");
                    successes.incrementAndGet();
                } catch (DomainConflictException ex) {
                    conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        VolunteerTask refreshed = taskRepository.findById(singleSeatTask.getId()).orElseThrow();
        assertThat(refreshed.getApprovedCount()).isEqualTo(1);
        assertThat(applicationRepository.findByTaskIdAndStatusOrderByCreatedAtAsc(
                singleSeatTask.getId(), ApplicationStatus.APPROVED)).hasSize(1);
    }

    @Test
    @DisplayName("Вебінтерфейс MVC: публічні сторінки повертають HTML, а панель координатора захищена від волонтера")
    void webMvcPagesAndRoleProtectionWork() throws Exception {
        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("VoluntHub")));

        mockMvc.perform(get("/tasks/{id}", singleSeatTask.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Завдання з одним місцем")));

        mockMvc.perform(get("/coordinator/dashboard")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
                                new ua.op.edu.ipatov.volunteer.security.PlatformUserDetails(vol1))))
                .andExpect(status().isForbidden());
    }
}
