package ua.op.edu.ipatov.volunteer.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Заявка волонтера на участь у завданні з контролем переходів станів.
 */
@Entity
@Table(name = "applications")
public class TaskApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private VolunteerTask task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "volunteer_id", nullable = false)
    private UserAccount volunteer;

    @Column(nullable = false, length = 800)
    private String motivation;

    @Column(name = "match_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal matchScore = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private UserAccount decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TaskApplication() {
    }

    public TaskApplication(VolunteerTask task, UserAccount volunteer, String motivation, BigDecimal matchScore) {
        this.task = task;
        this.volunteer = volunteer;
        this.motivation = motivation;
        this.matchScore = matchScore;
    }

    public void approve(UserAccount coordinator, String note, Instant now) {
        if (this.status != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Погодити можна лише заявку у стані очікування");
        }
        this.status = ApplicationStatus.APPROVED;
        this.decidedBy = coordinator;
        this.decisionNote = note;
        this.decidedAt = now;
    }

    public void reject(UserAccount coordinator, String note, Instant now) {
        if (this.status != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Відхилити можна лише заявку у стані очікування");
        }
        this.status = ApplicationStatus.REJECTED;
        this.decidedBy = coordinator;
        this.decisionNote = note;
        this.decidedAt = now;
    }

    public void cancelByVolunteer(Instant now) {
        if (this.status != ApplicationStatus.PENDING && this.status != ApplicationStatus.APPROVED) {
            throw new IllegalStateException("Цю заявку вже не можна скасувати");
        }
        this.status = ApplicationStatus.CANCELLED;
        this.decidedAt = now;
    }

    public Long getId() {
        return id;
    }

    public VolunteerTask getTask() {
        return task;
    }

    public UserAccount getVolunteer() {
        return volunteer;
    }

    public String getMotivation() {
        return motivation;
    }

    public BigDecimal getMatchScore() {
        return matchScore;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public UserAccount getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
