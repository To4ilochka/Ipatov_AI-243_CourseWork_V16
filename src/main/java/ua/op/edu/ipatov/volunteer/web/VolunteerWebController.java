package ua.op.edu.ipatov.volunteer.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ua.op.edu.ipatov.volunteer.domain.UserAccount;
import ua.op.edu.ipatov.volunteer.domain.VolunteerTask;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.*;
import ua.op.edu.ipatov.volunteer.exception.AccessPolicyViolationException;
import ua.op.edu.ipatov.volunteer.exception.DomainConflictException;
import ua.op.edu.ipatov.volunteer.security.PlatformUserDetails;
import ua.op.edu.ipatov.volunteer.service.CatalogAndInitiativeService;
import ua.op.edu.ipatov.volunteer.service.SkillMatchRecommendationService;
import ua.op.edu.ipatov.volunteer.service.WorkflowCoordinationService;
import java.math.BigDecimal;

/**
 * Вебконтролер робочого кабінету волонтера: профіль навичок, персональні рекомендації,
 * подання та скасування заявок, оформлення підсумкових звітів про виконання.
 */
@Controller
@RequestMapping("/volunteer")
public class VolunteerWebController {

    private final CatalogAndInitiativeService catalogService;
    private final SkillMatchRecommendationService recommendationService;
    private final WorkflowCoordinationService workflowService;

    public VolunteerWebController(CatalogAndInitiativeService catalogService,
                                  SkillMatchRecommendationService recommendationService,
                                  WorkflowCoordinationService workflowService) {
        this.catalogService = catalogService;
        this.recommendationService = recommendationService;
        this.workflowService = workflowService;
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal PlatformUserDetails principal, Model model) {
        populateDashboardModel(principal.getId(), model);
        if (!model.containsAttribute("reportForm")) {
            model.addAttribute("reportForm", new ReportWebForm());
        }
        return "volunteer-dashboard";
    }

    @PostMapping("/applications")
    public String applyToTask(@AuthenticationPrincipal PlatformUserDetails principal,
                              @Valid @ModelAttribute("applicationForm") PublicWebController.SubmitApplicationForm form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes,
                              HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            UserAccount viewer = catalogService.getUserWithSkills(principal.getId());
            VolunteerTask task = catalogService.getTaskDetailsForPublicOrOwner(form.getTaskId(), viewer);
            model.addAttribute("task", catalogService.toTaskCardDto(task));
            model.addAttribute("acceptingNow", task.isAcceptingApplications(java.time.Instant.now()));
            model.addAttribute("matchExplanation", recommendationService.evaluateTaskForVolunteer(viewer, task));
            model.addAttribute("errorMessage", "Перевірте заповнення мотиваційного коментаря (від 15 до 800 символів).");
            return "task-detail";
        }
        try {
            ApplicationDto created = workflowService.submitApplication(
                    principal.getId(), new SubmitApplicationRequest(form.getTaskId(), form.getMotivation()));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Заявку #" + created.id() + " на завдання «" + created.taskTitle() + "» успішно подано!");
            return "redirect:/volunteer/dashboard";
        } catch (DomainConflictException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("applicationForm", form);
            return "redirect:/tasks/" + form.getTaskId();
        }
    }

    @PostMapping("/applications/{id}/cancel")
    public String cancelApplication(@AuthenticationPrincipal PlatformUserDetails principal,
                                    @PathVariable Long id,
                                    RedirectAttributes redirectAttributes) {
        try {
            workflowService.cancelApplicationByVolunteer(principal.getId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Заявку #" + id + " скасовано.");
        } catch (DomainConflictException | AccessPolicyViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/volunteer/dashboard";
    }

    @PostMapping("/skills")
    public String addOrUpdateSkill(@AuthenticationPrincipal PlatformUserDetails principal,
                                   @RequestParam Long skillId,
                                   @RequestParam short proficiencyLevel,
                                   @RequestParam(required = false) String notes,
                                   RedirectAttributes redirectAttributes) {
        catalogService.upsertVolunteerSkill(principal.getId(),
                new SkillSelectionRequest(skillId, proficiencyLevel, notes));
        redirectAttributes.addFlashAttribute("successMessage",
                "Навичку оновлено, персональні рекомендації перераховано.");
        return "redirect:/volunteer/dashboard";
    }

    @PostMapping("/skills/{skillId}/delete")
    public String removeSkill(@AuthenticationPrincipal PlatformUserDetails principal,
                              @PathVariable Long skillId,
                              RedirectAttributes redirectAttributes) {
        catalogService.removeVolunteerSkill(principal.getId(), skillId);
        redirectAttributes.addFlashAttribute("successMessage", "Навичку видалено з профілю.");
        return "redirect:/volunteer/dashboard";
    }

    @PostMapping("/reports")
    public String submitReport(@AuthenticationPrincipal PlatformUserDetails principal,
                               @Valid @ModelAttribute("reportForm") ReportWebForm form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes,
                               HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            populateDashboardModel(principal.getId(), model);
            model.addAttribute("errorMessage", "Виправте помилки у формі підсумкового звіту (введені дані збережено).");
            return "volunteer-dashboard";
        }
        try {
            ReportDto saved = workflowService.submitOrUpdateReport(principal.getId(),
                    new SubmitReportRequest(form.getApplicationId(), form.getSummary(),
                            form.getHoursSpent(), form.getImpactMetrics()));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Звіт #" + saved.id() + " збережено та передано координатору на перевірку.");
            return "redirect:/volunteer/dashboard";
        } catch (DomainConflictException | AccessPolicyViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/volunteer/dashboard";
        }
    }

    private void populateDashboardModel(Long volunteerId, Model model) {
        UserAccount profile = catalogService.getUserWithSkills(volunteerId);
        model.addAttribute("profile", profile);
        model.addAttribute("allSkills", catalogService.listAllSkills());
        model.addAttribute("recommendations", recommendationService.recommendTasksForVolunteer(volunteerId, 6));
        model.addAttribute("applications", workflowService.listVolunteerApplications(volunteerId));
        model.addAttribute("reports", workflowService.listVolunteerReports(volunteerId));
    }

    public static class ReportWebForm {
        @jakarta.validation.constraints.NotNull(message = "Оберіть погоджену заявку")
        private Long applicationId;

        @jakarta.validation.constraints.NotBlank(message = "Опишіть результати виконання завдання")
        @jakarta.validation.constraints.Size(min = 20, max = 2000, message = "Опис має містити від 20 до 2000 символів")
        private String summary = "";

        @jakarta.validation.constraints.NotNull(message = "Вкажіть витрачені години")
        @jakarta.validation.constraints.DecimalMin(value = "0.5", message = "Мінімум 0.5 год")
        @jakarta.validation.constraints.DecimalMax(value = "200.0", message = "Максимум 200 год")
        private BigDecimal hoursSpent = new BigDecimal("4.0");

        @jakarta.validation.constraints.Size(max = 400, message = "До 400 символів")
        private String impactMetrics = "";

        public Long getApplicationId() { return applicationId; }
        public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public BigDecimal getHoursSpent() { return hoursSpent; }
        public void setHoursSpent(BigDecimal hoursSpent) { this.hoursSpent = hoursSpent; }
        public String getImpactMetrics() { return impactMetrics; }
        public void setImpactMetrics(String impactMetrics) { this.impactMetrics = impactMetrics; }
    }
}
