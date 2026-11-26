package ua.op.edu.ipatov.volunteer.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Підсумковий звіт волонтера про фактичне виконання призначеного завдання.
 */
@Entity
@Table(name = "execution_reports")
public class ExecutionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private TaskApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserAccount author;

    @Column(nullable = false, length = 2000)
    private String summary;

    @Column(name = "hours_spent", nullable = false, precision = 5, scale = 1)
    private BigDecimal hoursSpent;

    @Column(name = "impact_metrics", length = 400)
    private String impactMetrics;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReportStatus status = ReportStatus.SUBMITTED;

    @Column(name = "coordinator_feedback", length = 500)
    private String coordinatorFeedback;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    public ExecutionReport() {
    }

    public ExecutionReport(TaskApplication application, UserAccount author,
                           String summary, BigDecimal hoursSpent, String impactMetrics) {
        this.application = application;
        this.author = author;
        this.summary = summary;
        this.hoursSpent = hoursSpent;
        this.impactMetrics = impactMetrics;
    }

    public void updateByAuthor(String newSummary, BigDecimal newHours, String newMetrics, Instant now) {
        if (this.status == ReportStatus.VERIFIED) {
            throw new IllegalStateException("Підтверджений звіт не підлягає редагуванню");
        }
        this.summary = newSummary;
        this.hoursSpent = newHours;
        this.impactMetrics = newMetrics;
        this.status = ReportStatus.SUBMITTED;
        this.updatedAt = now;
    }

    public void verify(String feedback, Instant now) {
        this.status = ReportStatus.VERIFIED;
        this.coordinatorFeedback = feedback;
        this.verifiedAt = now;
        this.updatedAt = now;
    }

    public void requestRevision(String feedback, Instant now) {
        this.status = ReportStatus.NEEDS_REVISION;
        this.coordinatorFeedback = feedback;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public TaskApplication getApplication() {
        return application;
    }

    public UserAccount getAuthor() {
        return author;
    }

    public String getSummary() {
        return summary;
    }

    public BigDecimal getHoursSpent() {
        return hoursSpent;
    }

    public String getImpactMetrics() {
        return impactMetrics;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getCoordinatorFeedback() {
        return coordinatorFeedback;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public Long getVersion() {
        return version;
    }
}
