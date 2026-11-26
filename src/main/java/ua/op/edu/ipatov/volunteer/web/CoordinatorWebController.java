package ua.op.edu.ipatov.volunteer.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ua.op.edu.ipatov.volunteer.domain.*;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.*;
import ua.op.edu.ipatov.volunteer.exception.AccessPolicyViolationException;
import ua.op.edu.ipatov.volunteer.exception.DomainConflictException;
import ua.op.edu.ipatov.volunteer.security.PlatformUserDetails;
import ua.op.edu.ipatov.volunteer.service.CatalogAndInitiativeService;
import ua.op.edu.ipatov.volunteer.service.WorkflowCoordinationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Вебконтролер панелі координатора: створення ініціатив і завдань, погодження/відхилення заявок,
 * перегляд авторизованих контактів учасників та верифікація підсумкових звітів.
 */
@Controller
@RequestMapping("/coordinator")
public class CoordinatorWebController {

    private final CatalogAndInitiativeService catalogService;
    private final WorkflowCoordinationService workflowService;

    public CoordinatorWebController(CatalogAndInitiativeService catalogService,
                                    WorkflowCoordinationService workflowService) {
        this.catalogService = catalogService;
        this.workflowService = workflowService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal PlatformUserDetails principal, Model model) {
        populateCoordinatorModel(principal.getId(), model);
        if (!model.containsAttribute("initiativeForm")) {
            InitiativeWebForm f = new InitiativeWebForm();
            f.setStartsOn(LocalDate.now());
            f.setEndsOn(LocalDate.now().plusDays(45));
            model.addAttribute("initiativeForm", f);
        }
        if (!model.containsAttribute("taskForm")) {
            TaskWebForm tf = new TaskWebForm();
            tf.setDeadlineDate(LocalDate.now().plusDays(14));
            model.addAttribute("taskForm", tf);
        }
        return "coordinator-dashboard";
    }

    @PostMapping("/initiatives")
    public String createInitiative(@AuthenticationPrincipal PlatformUserDetails principal,
                                   @Valid @ModelAttribute("initiativeForm") InitiativeWebForm form,
                                   BindingResult bindingResult,
                                   Model model,
                                   RedirectAttributes redirectAttributes,
                                   HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            populateCoordinatorModel(principal.getId(), model);
            if (!model.containsAttribute("taskForm")) {
                model.addAttribute("taskForm", new TaskWebForm());
            }
            model.addAttribute("errorMessage", "Перевірте поля форми створення ініціативи.");
            return "coordinator-dashboard";
        }
        try {
            Initiative created = catalogService.createInitiative(principal.getId(),
                    new CreateInitiativeRequest(form.getTitle(), form.getCategory(), form.getLocationCity(),
                            form.getSummary(), form.getStatus(), form.getStartsOn(), form.getEndsOn()));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Ініціативу «" + created.getTitle() + "» успішно створено.");
            return "redirect:/coordinator/dashboard";
        } catch (DomainConflictException | AccessPolicyViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/coordinator/dashboard";
        }
    }

    @PostMapping("/tasks")
    public String createTask(@AuthenticationPrincipal PlatformUserDetails principal,
                             @Valid @ModelAttribute("taskForm") TaskWebForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes,
                             HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            populateCoordinatorModel(principal.getId(), model);
            if (!model.containsAttribute("initiativeForm")) {
                model.addAttribute("initiativeForm", new InitiativeWebForm());
            }
            model.addAttribute("errorMessage", "Виправте помилки у формі нового завдання (введені дані збережено).");
            return "coordinator-dashboard";
        }
        try {
            List<TaskSkillRequirementInput> skills = new ArrayList<>();
            if (form.getPrimarySkillId() != null) {
                skills.add(new TaskSkillRequirementInput(
                        form.getPrimarySkillId(), form.getPrimarySkillMinLevel(), form.isPrimarySkillMandatory(), (short) 5));
            }
            Instant deadline = form.getDeadlineDate().atTime(18, 0)
                    .atZone(ZoneId.of("Europe/Kyiv")).toInstant();
            VolunteerTask created = catalogService.createTask(principal.getId(),
                    new CreateTaskRequest(form.getInitiativeId(), form.getTitle(), form.getDescription(),
                            form.getLocationText(), form.getWorkFormat(), form.getUrgency(),
                            form.getMaxVolunteers(), form.getEstimatedHours(), deadline, skills));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Завдання «" + created.getTitle() + "» опубліковано в ініціативі.");
            return "redirect:/coordinator/dashboard";
        } catch (DomainConflictException | AccessPolicyViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/coordinator/dashboard";
        }
    }

    @PostMapping("/applications/{id}/approve")
    public String approveApplication(@AuthenticationPrincipal PlatformUserDetails principal,
                                     @PathVariable Long id,
                                     @RequestParam(defaultValue = "Погоджено координатором до виконання") String decisionNote,
                                     RedirectAttributes redirectAttributes) {
        try {
            workflowService.approveApplication(principal.getId(), id, decisionNote);
            redirectAttributes.addFlashAttribute("successMessage", "Заявку #" + id + " погоджено, місце зарезервовано.");
        } catch (DomainConflictException | AccessPolicyViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/coordinator/dashboard";
    }

    @PostMapping("/applications/{id}/reject")
    public String rejectApplication(@AuthenticationPrincipal PlatformUserDetails principal,
                                    @PathVariable Long id,
                                    @RequestParam String decisionNote,
                                    RedirectAttributes redirectAttributes) {
        try {
            workflowService.rejectApplication(principal.getId(), id, decisionNote);
            redirectAttributes.addFlashAttribute("successMessage", "Заявку #" + id + " відхилено з фіксацією причини.");
        } catch (DomainConflictException | AccessPolicyViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/coordinator/dashboard";
    }

    @PostMapping("/reports/{id}/review")
    public String reviewReport(@AuthenticationPrincipal PlatformUserDetails principal,
                               @PathVariable Long id,
                               @RequestParam ReportStatus targetStatus,
                               @RequestParam String feedback,
                               RedirectAttributes redirectAttributes) {
        try {
            workflowService.reviewReportByCoordinator(principal.getId(), id, new VerifyReportRequest(targetStatus, feedback));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Рішення щодо звіту #" + id + " (" + targetStatus.getLabelUk() + ") збережено.");
        } catch (DomainConflictException | AccessPolicyViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/coordinator/dashboard";
    }

    private void populateCoordinatorModel(Long coordinatorId, Model model) {
        model.addAttribute("coordinator", catalogService.getUserWithSkills(coordinatorId));
        model.addAttribute("initiatives", catalogService.listCoordinatorInitiatives(coordinatorId));
        model.addAttribute("applications", workflowService.listCoordinatorApplications(coordinatorId));
        model.addAttribute("reports", workflowService.listCoordinatorReports(coordinatorId));
        model.addAttribute("skills", catalogService.listAllSkills());
        model.addAttribute("workFormats", WorkFormat.values());
        model.addAttribute("urgencies", TaskUrgency.values());
        model.addAttribute("initiativeStatuses", List.of(InitiativeStatus.ACTIVE, InitiativeStatus.DRAFT));
    }

    public static class InitiativeWebForm {
        @jakarta.validation.constraints.NotBlank(message = "Вкажіть назву ініціативи")
        @jakarta.validation.constraints.Size(min = 5, max = 160, message = "Назва має містити від 5 до 160 символів")
        private String title = "";

        @jakarta.validation.constraints.NotBlank(message = "Вкажіть категорію")
        private String category = "Освіта та ІТ";

        @jakarta.validation.constraints.NotBlank(message = "Вкажіть місто")
        private String locationCity = "Одеса";

        @jakarta.validation.constraints.NotBlank(message = "Додайте публічний опис")
        @jakarta.validation.constraints.Size(min = 20, max = 1500, message = "Опис має містити від 20 до 1500 символів")
        private String summary = "";

        private InitiativeStatus status = InitiativeStatus.ACTIVE;

        @jakarta.validation.constraints.NotNull(message = "Вкажіть дату початку")
        private LocalDate startsOn = LocalDate.now();

        @jakarta.validation.constraints.NotNull(message = "Вкажіть дату завершення")
        private LocalDate endsOn = LocalDate.now().plusDays(30);

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getLocationCity() { return locationCity; }
        public void setLocationCity(String locationCity) { this.locationCity = locationCity; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public InitiativeStatus getStatus() { return status; }
        public void setStatus(InitiativeStatus status) { this.status = status; }
        public LocalDate getStartsOn() { return startsOn; }
        public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }
        public LocalDate getEndsOn() { return endsOn; }
        public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }
    }

    public static class TaskWebForm {
        @jakarta.validation.constraints.NotNull(message = "Оберіть ініціативу")
        private Long initiativeId;

        @jakarta.validation.constraints.NotBlank(message = "Вкажіть назву завдання")
        @jakarta.validation.constraints.Size(min = 5, max = 160, message = "Від 5 до 160 символів")
        private String title = "";

        @jakarta.validation.constraints.NotBlank(message = "Додайте опис завдання")
        @jakarta.validation.constraints.Size(min = 20, max = 2000, message = "Опис має містити від 20 до 2000 символів")
        private String description = "";

        @jakarta.validation.constraints.NotBlank(message = "Вкажіть текстову локацію або канал зв'язку")
        @jakarta.validation.constraints.Size(min = 4, max = 200, message = "Від 4 до 200 символів")
        private String locationText = "";

        private WorkFormat workFormat = WorkFormat.ONSITE;
        private TaskUrgency urgency = TaskUrgency.NORMAL;

        @jakarta.validation.constraints.Min(value = 1, message = "Ліміт учасників — щонайменше 1")
        @jakarta.validation.constraints.Max(value = 500, message = "Максимум 500")
        private int maxVolunteers = 3;

        @jakarta.validation.constraints.NotNull(message = "Вкажіть години")
        @jakarta.validation.constraints.DecimalMin(value = "0.5", message = "Мінімум 0.5 год")
        private BigDecimal estimatedHours = new BigDecimal("4.0");

        @jakarta.validation.constraints.NotNull(message = "Вкажіть кінцеву дату реєстрації")
        private LocalDate deadlineDate = LocalDate.now().plusDays(14);

        private Long primarySkillId;
        private short primarySkillMinLevel = 2;
        private boolean primarySkillMandatory = true;

        public Long getInitiativeId() { return initiativeId; }
        public void setInitiativeId(Long initiativeId) { this.initiativeId = initiativeId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getLocationText() { return locationText; }
        public void setLocationText(String locationText) { this.locationText = locationText; }
        public WorkFormat getWorkFormat() { return workFormat; }
        public void setWorkFormat(WorkFormat workFormat) { this.workFormat = workFormat; }
        public TaskUrgency getUrgency() { return urgency; }
        public void setUrgency(TaskUrgency urgency) { this.urgency = urgency; }
        public int getMaxVolunteers() { return maxVolunteers; }
        public void setMaxVolunteers(int maxVolunteers) { this.maxVolunteers = maxVolunteers; }
        public BigDecimal getEstimatedHours() { return estimatedHours; }
        public void setEstimatedHours(BigDecimal estimatedHours) { this.estimatedHours = estimatedHours; }
        public LocalDate getDeadlineDate() { return deadlineDate; }
        public void setDeadlineDate(LocalDate deadlineDate) { this.deadlineDate = deadlineDate; }
        public Long getPrimarySkillId() { return primarySkillId; }
        public void setPrimarySkillId(Long primarySkillId) { this.primarySkillId = primarySkillId; }
        public short getPrimarySkillMinLevel() { return primarySkillMinLevel; }
        public void setPrimarySkillMinLevel(short primarySkillMinLevel) { this.primarySkillMinLevel = primarySkillMinLevel; }
        public boolean isPrimarySkillMandatory() { return primarySkillMandatory; }
        public void setPrimarySkillMandatory(boolean primarySkillMandatory) { this.primarySkillMandatory = primarySkillMandatory; }
    }
}
