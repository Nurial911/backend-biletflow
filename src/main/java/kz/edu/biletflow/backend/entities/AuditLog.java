package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Append-only history of organizer, staff and admin actions. Ids are plain values rather than
 * relations, and the actor's email is a copy, so an entry stays readable after a user is soft-deleted.
 */
@Entity
@Immutable
@Table(name = "audit_log", indexes = @Index(name = "idx_audit_log_event_created", columnList = "event_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_email", updatable = false)
    private String actorEmail;

    // AuditAction name. A plain string on purpose: @Enumerated makes Hibernate create a CHECK constraint
    // listing today's values, and ddl-auto=update never widens it, so new actions would fail to insert.
    @Column(name = "action", nullable = false, updatable = false, length = 64)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    // Null for actions that don't belong to an event (e.g. a role change).
    @Column(name = "event_id", updatable = false)
    private Long eventId;

    @Column(name = "description", nullable = false, updatable = false, length = 500)
    private String description;

    public AuditLog(Long actorId, String actorEmail, AuditAction action, Long entityId, Long eventId, String description) {
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.action = action.name();
        this.entityType = action.getEntityType();
        this.entityId = entityId;
        this.eventId = eventId;
        this.description = description;
    }
}
