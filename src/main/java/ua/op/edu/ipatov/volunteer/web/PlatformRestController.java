package ua.op.edu.ipatov.volunteer.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ua.op.edu.ipatov.volunteer.domain.TaskUrgency;
import ua.op.edu.ipatov.volunteer.domain.WorkFormat;
import ua.op.edu.ipatov.volunteer.dto.ApiDtos.*;
import ua.op.edu.ipatov.volunteer.security.PlatformUserDetails;
import ua.op.edu.ipatov.volunteer.service.CatalogAndInitiativeService;
import ua.op.edu.ipatov.volunteer.service.SkillMatchRecommendationService;
import ua.op.edu.ipatov.volunteer.service.WorkflowCoordinationService;
import java.util.List;

/**
 * Програмний REST-інтерфейс платформи з розмежуванням прав на рівні ролі та власника об'єкта.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "VoluntHub API", description = "Контракт REST-операцій пошуку завдань, рекомендацій, заявок, контактів та звітів")
public class PlatformRestController {

    private final CatalogAndInitiativeService catalogService;
    private final SkillMatchRecommendationService recommendationService;
    private final WorkflowCoordinationService workflowService;

    public PlatformRestController(CatalogAndInitiativeService catalogService,
                                  SkillMatchRecommendationService recommendationService,
                                  WorkflowCoordinationService workflowService) {
        this.catalogService = catalogService;
        this.recommendationService = recommendationService;
        this.workflowService = workflowService;
    }

    @GetMapping("/public/tasks")
    @Operation(summary = "Публічний пошук і фільтрація волонтерських завдань із пагінацією")
    public Page<TaskCardDto> listPublicTasks(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) WorkFormat workFormat,
            @RequestParam(required = false) TaskUrgency urgency,
            @RequestParam(required = false) Long skillId,
            @RequestParam(defaultValue = "false") boolean onlyOpen,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @RequestParam(defaultValue = "deadlineAt") String sortBy) {
        return catalogService.searchPublicTasks(
                query, category, city, workFormat, urgency, skillId, onlyOpen, page, size, sortBy);
    }

    @GetMapping("/public/tasks/{id}")
    @Operation(summary = "Публічна картка завдання (без закритих контактів учасників)")
    public TaskCardDto getPublicTask(@PathVariable Long id,
                                     @AuthenticationPrincipal PlatformUserDetails principal) {
        var viewer = principal != null ? catalogService.getUserWithSkills(principal.getId()) : null;
        return catalogService.toTaskCardDto(catalogService.getTaskDetailsForPublicOrOwner(id, viewer));
    }

    @GetMapping("/volunteer/recommendations")
    @Operation(summary = "Персоналізований підбір завдань за навичками з поясненням оцінки")
    public List<RecommendedTaskDto> getVolunteerRecommendations(
            @AuthenticationPrincipal PlatformUserDetails principal,
            @RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(30, Math.max(1, limit));
        return recommendationService.recommendTasksForVolunteer(principal.getId(), safeLimit);
    }

    @PostMapping("/volunteer/skills")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Додавання або оновлення навички у профілі волонтера")
    public void upsertSkill(@AuthenticationPrincipal PlatformUserDetails principal,
                            @Valid @RequestBody SkillSelectionRequest request) {
        catalogService.upsertVolunteerSkill(principal.getId(), request);
    }

    @DeleteMapping("/volunteer/skills/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Видалення навички з профілю волонтера")
    public void deleteSkill(@AuthenticationPrincipal PlatformUserDetails principal,
                            @PathVariable Long skillId) {
        catalogService.removeVolunteerSkill(principal.getId(), skillId);
    }

    @PostMapping("/volunteer/applications")
    @Operation(summary = "Подання заявки волонтера на участь у відкритому завданні")
    public ResponseEntity<ApplicationDto> submitApplication(
            @AuthenticationPrincipal PlatformUserDetails principal,
            @Valid @RequestBody SubmitApplicationRequest request) {
        ApplicationDto created = workflowService.submitApplication(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/volunteer/applications")
    @Operation(summary = "Перегляд власних заявок волонтера")
    public List<ApplicationDto> listMyApplications(@AuthenticationPrincipal PlatformUserDetails principal) {
        return workflowService.listVolunteerApplications(principal.getId());
    }

    @PostMapping("/volunteer/applications/{id}/cancel")
    @Operation(summary = "Скасування власної заявки волонтером")
    public ApplicationDto cancelMyApplication(@AuthenticationPrincipal PlatformUserDetails principal,
                                              @PathVariable Long id) {
        return workflowService.cancelApplicationByVolunteer(principal.getId(), id);
    }

    @PostMapping("/volunteer/reports")
    @Operation(summary = "Подання підсумкового звіту про виконання завдання")
    public ResponseEntity<ReportDto> submitReport(@AuthenticationPrincipal PlatformUserDetails principal,
                                                  @Valid @RequestBody SubmitReportRequest request) {
        ReportDto report = workflowService.submitOrUpdateReport(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    @PutMapping("/volunteer/reports/{reportId}")
    @Operation(summary = "Редагування власного непідтвердженого звіту (захист від зміни чужого звіту)")
    public ReportDto updateReport(@AuthenticationPrincipal PlatformUserDetails principal,
                                  @PathVariable Long reportId,
                                  @Valid @RequestBody SubmitReportRequest request) {
        return workflowService.updateExistingReportById(principal.getId(), reportId, request);
    }

    @PostMapping("/coordinator/initiatives")
    @Operation(summary = "Створення нової волонтерської ініціативи координатором")
    public ResponseEntity<Long> createInitiative(@AuthenticationPrincipal PlatformUserDetails principal,
                                                 @Valid @RequestBody CreateInitiativeRequest request) {
        var created = catalogService.createInitiative(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created.getId());
    }

    @PostMapping("/coordinator/tasks")
    @Operation(summary = "Створення завдання в межах власної ініціативи координатора")
    public ResponseEntity<TaskCardDto> createTask(@AuthenticationPrincipal PlatformUserDetails principal,
                                                  @Valid @RequestBody CreateTaskRequest request) {
        var created = catalogService.createTask(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.toTaskCardDto(created));
    }

    @GetMapping("/coordinator/applications")
    @Operation(summary = "Перегляд заявок на завдання власних ініціатив координатора")
    public List<ApplicationDto> listCoordinatorApplications(@AuthenticationPrincipal PlatformUserDetails principal) {
        return workflowService.listCoordinatorApplications(principal.getId());
    }

    @PostMapping("/coordinator/applications/{id}/approve")
    @Operation(summary = "Погодження заявки волонтера та призначення на завдання")
    public ApplicationDto approveApplication(@AuthenticationPrincipal PlatformUserDetails principal,
                                             @PathVariable Long id,
                                             @Valid @RequestBody CoordinatorDecisionRequest request) {
        return workflowService.approveApplication(principal.getId(), id, request.decisionNote());
    }

    @PostMapping("/coordinator/applications/{id}/reject")
    @Operation(summary = "Вмотивоване відхилення заявки волонтера")
    public ApplicationDto rejectApplication(@AuthenticationPrincipal PlatformUserDetails principal,
                                            @PathVariable Long id,
                                            @Valid @RequestBody CoordinatorDecisionRequest request) {
        return workflowService.rejectApplication(principal.getId(), id, request.decisionNote());
    }

    @PostMapping("/coordinator/reports/{id}/review")
    @Operation(summary = "Верифікація або повернення на доопрацювання звіту про виконання")
    public ReportDto reviewReport(@AuthenticationPrincipal PlatformUserDetails principal,
                                  @PathVariable Long id,
                                  @Valid @RequestBody VerifyReportRequest request) {
        return workflowService.reviewReportByCoordinator(principal.getId(), id, request);
    }

    @GetMapping("/applications/{id}/contacts")
    @Operation(summary = "Захищений доступ до контактів учасника виключно для уповноважених користувачів")
    public ParticipantContactDto getAuthorizedContacts(@AuthenticationPrincipal PlatformUserDetails principal,
                                                       @PathVariable Long id) {
        return workflowService.getApplicationContacts(principal.getId(), id);
    }
}
