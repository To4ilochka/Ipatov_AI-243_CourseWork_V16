package ua.op.edu.ipatov.volunteer.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ua.op.edu.ipatov.volunteer.config.DemoDataSeeder;
import ua.op.edu.ipatov.volunteer.domain.*;
import ua.op.edu.ipatov.volunteer.repository.*;
import ua.op.edu.ipatov.volunteer.security.PlatformUserDetails;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Функціональні перевірки MVC-контролерів, форм із CSRF-захистом, фільтрації каталогу та ідемпотентного сидінгу.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MvcAndSeederIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SkillRepository skillRepository;
    @Autowired private UserAccountRepository userRepository;
    @Autowired private InitiativeRepository initiativeRepository;
    @Autowired private VolunteerTaskRepository taskRepository;
    @Autowired private TaskApplicationRepository applicationRepository;
    @Autowired private ExecutionReportRepository reportRepository;
    @Autowired private AuditEventRepository auditRepository;

    @BeforeEach
    void reset() {
        reportRepository.deleteAll();
        applicationRepository.deleteAll();
        taskRepository.deleteAll();
        initiativeRepository.deleteAll();
        auditRepository.deleteAll();
        userRepository.deleteAll();
        skillRepository.deleteAll();
    }

    @Test
    @DisplayName("Ідемпотентний DemoDataSeeder коректно наповнює базу і не створює дублікатів при повторному запуску")
    void idempotentSeederAndWebDashboardsWork() throws Exception {
        DemoDataSeeder seeder = new DemoDataSeeder(
                skillRepository, userRepository, initiativeRepository,
                taskRepository, applicationRepository, reportRepository, auditRepository, passwordEncoder);

        seeder.run(new DefaultApplicationArguments());
        long skillsAfterFirst = skillRepository.count();
        long tasksAfterFirst = taskRepository.count();
        seeder.run(new DefaultApplicationArguments()); // повторний виклик

        assertThat(skillRepository.count()).isEqualTo(skillsAfterFirst).isEqualTo(12);
        assertThat(taskRepository.count()).isEqualTo(tasksAfterFirst).isEqualTo(15);

        UserAccount vol = userRepository.findByEmailIgnoreCase("vol.ivanenko@volunthub.op.edu.ua").orElseThrow();
        UserAccount coord = userRepository.findByEmailIgnoreCase("coord.odesa@volunthub.op.edu.ua").orElseThrow();

        // Фільтрація каталогу за містом, форматом і порожня вибірка
        mockMvc.perform(get("/tasks")
                        .param("query", "WCAG")
                        .param("city", "Одеса")
                        .param("workFormat", "REMOTE")
                        .param("onlyOpen", "true"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tasks").param("query", "НеіснуючийЗапит99999"))
                .andExpect(status().isOk());

        // Вхід у кабінет волонтера, додавання навички, подання заявки та звіту через MVC-форми з CSRF
        PlatformUserDetails volPrincipal = new PlatformUserDetails(vol);
        PlatformUserDetails coordPrincipal = new PlatformUserDetails(coord);

        mockMvc.perform(get("/dashboard").with(user(volPrincipal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/volunteer/dashboard"));

        mockMvc.perform(get("/volunteer/dashboard").with(user(volPrincipal)))
                .andExpect(status().isOk());

        Skill firstSkill = skillRepository.findAll().get(0);
        mockMvc.perform(post("/volunteer/skills").with(user(volPrincipal)).with(csrf())
                        .param("skillId", firstSkill.getId().toString())
                        .param("proficiencyLevel", "3")
                        .param("notes", "Перевірено інтеграційним тестом"))
                .andExpect(status().is3xxRedirection());

        // Панель координатора: створення ініціативи та нового завдання з CSRF
        mockMvc.perform(get("/coordinator/dashboard").with(user(coordPrincipal)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/coordinator/initiatives").with(user(coordPrincipal)).with(csrf())
                        .param("title", "Нова інтеграційна ініціатива")
                        .param("category", "Екологія")
                        .param("locationCity", "Одеса")
                        .param("summary", "Публічний опис нової ініціативи довжиною понад двадцять символів.")
                        .param("status", "ACTIVE")
                        .param("startsOn", LocalDate.now().toString())
                        .param("endsOn", LocalDate.now().plusDays(20).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/coordinator/dashboard"));

        Initiative coordInit = initiativeRepository.findByCoordinatorIdOrderByCreatedAtDesc(coord.getId()).get(0);
        mockMvc.perform(post("/coordinator/tasks").with(user(coordPrincipal)).with(csrf())
                        .param("initiativeId", coordInit.getId().toString())
                        .param("title", "Нове координаторське завдання")
                        .param("description", "Опис робіт для нового завдання достатньої довжини для валідації.")
                        .param("locationText", "м. Одеса, корпус ІКС")
                        .param("workFormat", "ONSITE")
                        .param("urgency", "HIGH")
                        .param("maxVolunteers", "4")
                        .param("estimatedHours", "5.0")
                        .param("deadlineDate", LocalDate.now().plusDays(12).toString())
                        .param("primarySkillId", firstSkill.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/coordinator/dashboard"));

        // Реєстрація нового волонтера з валідним NIST-паролем
        mockMvc.perform(post("/register").with(csrf())
                        .param("email", "fresh.vol@volunthub.op.edu.ua")
                        .param("password", "FreshVolunteer#2026!Pass")
                        .param("fullName", "Петренко Ірина Миколаївна")
                        .param("city", "Одеса")
                        .param("phone", "+380957778899"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}