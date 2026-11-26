package ua.op.edu.ipatov.volunteer.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Запис аудиту змін ключових предметних станів (заявок, завдань, звітів).
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 600)
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt = Instant.now();

    public AuditEvent() {
    }

    public AuditEvent(String entityType, Long entityId, Long actorId, String action, String details) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.actorId = actorId;
        this.action = action;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
