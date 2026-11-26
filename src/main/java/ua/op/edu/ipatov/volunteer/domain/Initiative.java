package ua.op.edu.ipatov.volunteer.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Доменна сутність волонтерської ініціативи, якою керує відповідальний координатор.
 */
@Entity
@Table(name = "initiatives")
public class Initiative {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coordinator_id", nullable = false)
    private UserAccount coordinator;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(name = "location_city", nullable = false, length = 80)
    private String locationCity;

    @Column(nullable = false, length = 1500)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InitiativeStatus status = InitiativeStatus.ACTIVE;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "initiative", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VolunteerTask> tasks = new ArrayList<>();

    public Initiative() {
    }

    public Initiative(UserAccount coordinator, String title, String category, String locationCity,
                      String summary, InitiativeStatus status, LocalDate startsOn, LocalDate endsOn) {
        this.coordinator = coordinator;
        this.title = title;
        this.category = category;
        this.locationCity = locationCity;
        this.summary = summary;
        this.status = status;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }

    /**
     * Перевіряє, чи відкрита ініціатива для подання нових заявок.
     */
    public boolean isActiveForApplications() {
        return this.status == InitiativeStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getCoordinator() {
        return coordinator;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLocationCity() {
        return locationCity;
    }

    public void setLocationCity(String locationCity) {
        this.locationCity = locationCity;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public InitiativeStatus getStatus() {
        return status;
    }

    public void setStatus(InitiativeStatus status) {
        this.status = status;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public void setStartsOn(LocalDate startsOn) {
        this.startsOn = startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public void setEndsOn(LocalDate endsOn) {
        this.endsOn = endsOn;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<VolunteerTask> getTasks() {
        return tasks;
    }
}
