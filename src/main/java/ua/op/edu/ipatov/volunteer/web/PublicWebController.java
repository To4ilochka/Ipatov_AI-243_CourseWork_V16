package ua.op.edu.ipatov.volunteer.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
import ua.op.edu.ipatov.volunteer.exception.ResourceNotFoundException;
import ua.op.edu.ipatov.volunteer.security.PlatformUserDetails;
import ua.op.edu.ipatov.volunteer.service.CatalogAndInitiativeService;
import ua.op.edu.ipatov.volunteer.service.SkillMatchRecommendationService;

/**
 * Вебконтролер публічного каталогу завдань, сторінки деталей, реєстрації та переадресації за роллю.
 */
@Controller
public class PublicWebController {

    private final CatalogAndInitiativeService catalogService;
    private final SkillMatchRecommendationService recommendationService;

    public PublicWebController(CatalogAndInitiativeService catalogService,
                               SkillMatchRecommendationService recommendationService) {
        this.catalogService = catalogService;
        this.recommendationService = recommendationService;
    }

    @GetMapping({"/", "/tasks"})
    public String catalog(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) WorkFormat workFormat,
            @RequestParam(required = false) TaskUrgency urgency,
            @RequestParam(required = false) Long skillId,
            @RequestParam(defaultValue = "false") boolean onlyOpen,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            Model model) {
        Page<TaskCardDto> tasksPage = catalogService.searchPublicTasks(
                query, category, city, workFormat, urgency, skillId, onlyOpen, page, size, "deadlineAt");
        model.addAttribute("tasksPage", tasksPage);
        model.addAttribute("skills", catalogService.listAllSkills());
        model.addAttribute("workFormats", WorkFormat.values());
        model.addAttribute("urgencies", TaskUrgency.values());
        model.addAttribute("query", query);
        model.addAttribute("category", category);
        model.addAttribute("city", city);
        model.addAttribute("selectedFormat", workFormat);
        model.addAttribute("selectedUrgency", urgency);
        model.addAttribute("selectedSkillId", skillId);
        model.addAttribute("onlyOpen", onlyOpen);
        return "catalog";
    }

    @GetMapping("/tasks/{id}")
    public String taskDetail(@PathVariable Long id,
                             @AuthenticationPrincipal PlatformUserDetails principal,
                             Model model,
                             HttpServletResponse response) {
        try {
            UserAccount viewer = principal != null ? catalogService.getUserWithSkills(principal.getId()) : null;
            VolunteerTask task = catalogService.getTaskDetailsForPublicOrOwner(id, viewer);
            model.addAttribute("task", catalogService.toTaskCardDto(task));
            model.addAttribute("acceptingNow", task.isAcceptingApplications(java.time.Instant.now()));
            if (viewer != null && viewer.getRole() == UserRole.VOLUNTEER) {
                model.addAttribute("matchExplanation", recommendationService.evaluateTaskForVolunteer(viewer, task));
            }
            if (!model.containsAttribute("applicationForm")) {
                model.addAttribute("applicationForm", new SubmitApplicationForm(task.getId(), ""));
            }
            return "task-detail";
        } catch (AccessPolicyViolationException ex) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            model.addAttribute("reason", ex.getMessage());
            return "access-denied";
        } catch (ResourceNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("reason", ex.getMessage());
            return "access-denied";
        }
    }

    @GetMapping("/dashboard")
    public String redirectDashboard(@AuthenticationPrincipal PlatformUserDetails principal) {
        if (principal == null) {
            return "redirect:/login";
        }
        return principal.getRole() == UserRole.COORDINATOR
                ? "redirect:/coordinator/dashboard"
                : "redirect:/volunteer/dashboard";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new RegisterForm("", "", "", "Одеса", "+380", "", ""));
        }
        return "register";
    }

    @PostMapping("/register")
    public String submitRegistration(@Valid @ModelAttribute("registerForm") RegisterForm form,
                                     BindingResult bindingResult,
                                     Model model,
                                     RedirectAttributes redirectAttributes,
                                     HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "register";
        }
        try {
            catalogService.registerVolunteer(new RegisterVolunteerRequest(
                    form.getEmail(), form.getPassword(), form.getFullName(),
                    form.getCity(), form.getPhone(), form.getTelegramHandle(), form.getBio()
            ));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Обліковий запис волонтера створено. Увійдіть за вашою поштою та паролем.");
            return "redirect:/login";
        } catch (DomainConflictException ex) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            model.addAttribute("errorMessage", ex.getMessage());
            return "register";
        }
    }

    @GetMapping("/access-denied")
    public String accessDenied(HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("reason", "У вашого облікового запису немає прав для перегляду або зміни цього розділу.");
        return "access-denied";
    }

    public static class SubmitApplicationForm {
        private Long taskId;
        @jakarta.validation.constraints.NotBlank(message = "Опишіть вашу мотивацію та релевантний досвід")
        @jakarta.validation.constraints.Size(min = 15, max = 800, message = "Мотивація має містити від 15 до 800 символів")
        private String motivation;

        public SubmitApplicationForm() {
        }

        public SubmitApplicationForm(Long taskId, String motivation) {
            this.taskId = taskId;
            this.motivation = motivation;
        }

        public Long getTaskId() {
            return taskId;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }

        public String getMotivation() {
            return motivation;
        }

        public void setMotivation(String motivation) {
            this.motivation = motivation;
        }
    }

    public static class RegisterForm {
        @jakarta.validation.constraints.NotBlank(message = "Вкажіть електронну пошту")
        @jakarta.validation.constraints.Email(message = "Некоректний формат електронної пошти")
        private String email;

        @jakarta.validation.constraints.NotBlank(message = "Вкажіть пароль")
        @jakarta.validation.constraints.Size(min = 15, max = 128, message = "Пароль має містити від 15 до 128 символів (NIST SP 800-63B-4)")
        private String password;

        @jakarta.validation.constraints.NotBlank(message = "Вкажіть прізвище та ім'я")
        @jakarta.validation.constraints.Size(min = 3, max = 120, message = "Від 3 до 120 символів")
        private String fullName;

        @jakarta.validation.constraints.NotBlank(message = "Вкажіть місто")
        private String city;

        @jakarta.validation.constraints.NotBlank(message = "Вкажіть телефон")
        @jakarta.validation.constraints.Pattern(regexp = "^\\+?[0-9\\-\\s]{10,20}$", message = "Формат телефону: +380XXXXXXXXX")
        private String phone;

        private String telegramHandle;
        private String bio;

        public RegisterForm() {
        }

        public RegisterForm(String email, String password, String fullName, String city, String phone, String telegramHandle, String bio) {
            this.email = email;
            this.password = password;
            this.fullName = fullName;
            this.city = city;
            this.phone = phone;
            this.telegramHandle = telegramHandle;
            this.bio = bio;
        }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getTelegramHandle() { return telegramHandle; }
        public void setTelegramHandle(String telegramHandle) { this.telegramHandle = telegramHandle; }
        public String getBio() { return bio; }
        public void setBio(String bio) { this.bio = bio; }
    }
}
