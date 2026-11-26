package ua.op.edu.ipatov.volunteer.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Доменна сутність окремого завдання в межах волонтерської ініціативи.
 * Інкапсулює інваріанти ліміту учасників, дедлайну та активності ініціативи.
 */
@Entity
@Table(name = "tasks")
public class VolunteerTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiative_id", nullable = false)
    private Initiative initiative;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "location_text", nullable = false, length = 200)
    private String locationText;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_format", nullable = false, length = 20)
    private WorkFormat workFormat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskUrgency urgency;

    @Column(name = "max_volunteers", nullable = false)
    private int maxVolunteers;

    @Column(name = "approved_count", nullable = false)
    private int approvedCount = 0;

    @Column(name = "estimated_hours", nullable = false, precision = 5, scale = 1)
    private BigDecimal estimatedHours;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status = TaskStatus.OPEN;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TaskRequiredSkill> requiredSkills = new ArrayList<>();

    public VolunteerTask() {
    }

    public VolunteerTask(Initiative initiative, String title, String description, String locationText,
                         WorkFormat workFormat, TaskUrgency urgency, int maxVolunteers,
                         BigDecimal estimatedHours, Instant deadlineAt) {
        this.initiative = initiative;
        this.title = title;
        this.description = description;
        this.locationText = locationText;
        this.workFormat = workFormat;
        this.urgency = urgency;
        this.maxVolunteers = maxVolunteers;
        this.estimatedHours = estimatedHours;
        this.deadlineAt = deadlineAt;
    }

    public int getRemainingSeats() {
        return Math.max(0, maxVolunteers - approvedCount);
    }

    public boolean hasAvailableSeat() {
        return approvedCount < maxVolunteers;
    }

    /**
     * Перевіряє, чи дозволяє поточний стан завдання та ініціативи приймати нові заявки.
     */
    public boolean isAcceptingApplications(Instant now) {
        return status == TaskStatus.OPEN
                && initiative != null
                && initiative.isActiveForApplications()
                && hasAvailableSeat()
                && deadlineAt.isAfter(now);
    }

    public void incrementApprovedCount() {
        if (!hasAvailableSeat()) {
            throw new IllegalStateException("Ліміт учасників для цього завдання вже вичерпано");
        }
        this.approvedCount += 1;
        if (this.approvedCount == this.maxVolunteers && this.status == TaskStatus.OPEN) {
            this.status = TaskStatus.IN_PROGRESS;
        }
    }

    public void decrementApprovedCount() {
        if (this.approvedCount > 0) {
            this.approvedCount -= 1;
            if (this.status == TaskStatus.IN_PROGRESS && hasAvailableSeat()) {
                this.status = TaskStatus.OPEN;
            }
        }
    }

    public Long getId() {
        return id;
    }

    public Initiative getInitiative() {
        return initiative;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocationText() {
        return locationText;
    }

    public void setLocationText(String locationText) {
        this.locationText = locationText;
    }

    public WorkFormat getWorkFormat() {
        return workFormat;
    }

    public void setWorkFormat(WorkFormat workFormat) {
        this.workFormat = workFormat;
    }

    public TaskUrgency getUrgency() {
        return urgency;
    }

    public void setUrgency(TaskUrgency urgency) {
        this.urgency = urgency;
    }

    public int getMaxVolunteers() {
        return maxVolunteers;
    }

    public void setMaxVolunteers(int maxVolunteers) {
        this.maxVolunteers = maxVolunteers;
    }

    public int getApprovedCount() {
        return approvedCount;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(Instant deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<TaskRequiredSkill> getRequiredSkills() {
        return requiredSkills;
    }
}
