package ua.op.edu.ipatov.volunteer.dto;

import jakarta.validation.constraints.*;
import ua.op.edu.ipatov.volunteer.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Компактний набір DTO запитів і відповідей REST та MVC шарів (без витоку ORM-сутностей і хешів паролів).
 */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record RegisterVolunteerRequest(
            @NotBlank(message = "Вкажіть електронну пошту")
            @Email(message = "Некоректний формат електронної пошти")
            @Size(max = 120) String email,

            @NotBlank(message = "Пароль обов'язковий")
            @Size(min = 15, max = 128, message = "За стандартом NIST SP 800-63B-4 довжина пароля має становити від 15 до 128 символів")
            String password,

            @NotBlank(message = "Вкажіть прізвище та ім'я")
            @Size(min = 3, max = 120, message = "ПІБ має містити від 3 до 120 символів")
            String fullName,

            @NotBlank(message = "Вкажіть місто")
            @Size(min = 2, max = 80) String city,

            @NotBlank(message = "Вкажіть контактний телефон")
            @Pattern(regexp = "^\\+?[0-9\\-\\s]{10,20}$", message = "Вкажіть коректний номер телефону")
            String phone,

            @Size(max = 64) String telegramHandle,
            @Size(max = 600) String bio
    ) {
    }

    public record SkillSelectionRequest(
            @NotNull(message = "Оберіть навичку зі списку") Long skillId,
            @Min(value = 1, message = "Мінімальний рівень — 1")
            @Max(value = 3, message = "Максимальний рівень — 3") short proficiencyLevel,
            @Size(max = 200) String notes
    ) {
    }

    public record CreateInitiativeRequest(
            @NotBlank(message = "Назва ініціативи обов'язкова")
            @Size(min = 5, max = 160, message = "Назва має містити від 5 до 160 символів")
            String title,

            @NotBlank(message = "Оберіть категорію ініціативи")
            @Size(max = 60) String category,

            @NotBlank(message = "Вкажіть місто проведення")
            @Size(min = 2, max = 80) String locationCity,

            @NotBlank(message = "Додайте публічний опис ініціативи")
            @Size(min = 20, max = 1500, message = "Опис має містити від 20 до 1500 символів")
            String summary,

            @NotNull(message = "Оберіть початковий статус") InitiativeStatus status,
            @NotNull(message = "Вкажіть дату початку") LocalDate startsOn,
            @NotNull(message = "Вкажіть дату завершення") LocalDate endsOn
    ) {
    }

    public record TaskSkillRequirementInput(
            @NotNull Long skillId,
            @Min(1) @Max(3) short minLevel,
            boolean mandatory,
            @Min(1) @Max(5) short weight
    ) {
    }

    public record CreateTaskRequest(
            @NotNull(message = "Вкажіть ініціативу") Long initiativeId,

            @NotBlank(message = "Назва завдання обов'язкова")
            @Size(min = 5, max = 160, message = "Назва завдання має містити від 5 до 160 символів")
            String title,

            @NotBlank(message = "Додайте докладний опис завдання")
            @Size(min = 20, max = 2000, message = "Опис завдання має містити від 20 до 2000 символів")
            String description,

            @NotBlank(message = "Вкажіть текстовий опис локації або спосіб зв'язку")
            @Size(min = 4, max = 200) String locationText,

            @NotNull(message = "Оберіть формат роботи") WorkFormat workFormat,
            @NotNull(message = "Оберіть терміновість") TaskUrgency urgency,

            @Min(value = 1, message = "Ліміт учасників має бути не менше 1")
            @Max(value = 500, message = "Ліміт учасників не може перевищувати 500")
            int maxVolunteers,

            @NotNull(message = "Вкажіть орієнтовні трудовитрати в годинах")
            @DecimalMin(value = "0.5", message = "Мінімальна тривалість — 0.5 год")
            @DecimalMax(value = "200.0", message = "Максимальна тривалість — 200 год")
            BigDecimal estimatedHours,

            @NotNull(message = "Вкажіть кінцевий строк подання заявок")
            Instant deadlineAt,

            List<TaskSkillRequirementInput> requiredSkills
    ) {
    }

    public record SubmitApplicationRequest(
            @NotNull(message = "Ідентифікатор завдання обов'язковий") Long taskId,

            @NotBlank(message = "Опишіть вашу готовність і релевантний досвід")
            @Size(min = 15, max = 800, message = "Мотиваційний коментар має містити від 15 до 800 символів")
            String motivation
    ) {
    }

    public record CoordinatorDecisionRequest(
            @NotBlank(message = "Додайте коментар рішення координатора")
            @Size(min = 5, max = 500, message = "Коментар рішення має містити від 5 до 500 символів")
            String decisionNote
    ) {
    }

    public record SubmitReportRequest(
            @NotNull(message = "Вкажіть ідентифікатор погодженої заявки") Long applicationId,

            @NotBlank(message = "Опишіть виконану роботу")
            @Size(min = 20, max = 2000, message = "Звіт має містити від 20 до 2000 символів")
            String summary,

            @NotNull(message = "Вкажіть фактично витрачені години")
            @DecimalMin(value = "0.5", message = "Мінімум 0.5 год")
            @DecimalMax(value = "200.0", message = "Максимум 200 год")
            BigDecimal hoursSpent,

            @Size(max = 400, message = "Показники результату — до 400 символів")
            String impactMetrics
    ) {
    }

    public record VerifyReportRequest(
            @NotNull ReportStatus targetStatus,
            @NotBlank(message = "Коментар перевірки звіту обов'язковий")
            @Size(min = 5, max = 500) String feedback
    ) {
    }

    public record RequiredSkillDto(
            Long skillId,
            String code,
            String title,
            short minLevel,
            boolean mandatory,
            short weight
    ) {
    }

    public record TaskCardDto(
            Long id,
            Long initiativeId,
            String initiativeTitle,
            String category,
            String city,
            String coordinatorName,
            String title,
            String description,
            String locationText,
            WorkFormat workFormat,
            TaskUrgency urgency,
            int maxVolunteers,
            int approvedCount,
            int remainingSeats,
            BigDecimal estimatedHours,
            Instant deadlineAt,
            TaskStatus status,
            List<RequiredSkillDto> requiredSkills
    ) {
    }

    public record RecommendationExplanationDto(
            double totalScore,
            double skillComponentPoints,
            double locationComponentPoints,
            double urgencyComponentPoints,
            boolean missingMandatorySkill,
            List<String> matchedSkills,
            List<String> missingOrInsufficientSkills,
            String locationReason,
            String summaryText
    ) {
    }

    public record RecommendedTaskDto(
            TaskCardDto task,
            RecommendationExplanationDto explanation
    ) {
    }

    public record ParticipantContactDto(
            Long volunteerId,
            String fullName,
            String city,
            boolean contactsAuthorized,
            String email,
            String phone,
            String telegramHandle
    ) {
    }

    public record ApplicationDto(
            Long id,
            Long taskId,
            String taskTitle,
            String initiativeTitle,
            Long volunteerId,
            String volunteerName,
            String motivation,
            BigDecimal matchScore,
            ApplicationStatus status,
            String decisionNote,
            Instant createdAt,
            Instant decidedAt,
            ParticipantContactDto contact
    ) {
    }

    public record ReportDto(
            Long id,
            Long applicationId,
            Long taskId,
            String taskTitle,
            Long authorId,
            String authorName,
            String summary,
            BigDecimal hoursSpent,
            String impactMetrics,
            ReportStatus status,
            String coordinatorFeedback,
            Instant submittedAt,
            Instant verifiedAt
    ) {
    }
}
