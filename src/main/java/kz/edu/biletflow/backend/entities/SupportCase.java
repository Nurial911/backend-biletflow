package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_cases", indexes = {
        @Index(name = "idx_support_cases_requester", columnList = "requester_id"),
        @Index(name = "idx_support_cases_event", columnList = "event_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SupportCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private SupportCaseType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private SupportCaseCategory category;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupportCaseStatus status = SupportCaseStatus.OPEN;

    // Handed to a platform admin's attention by the organizer team.
    @Column(name = "escalated", nullable = false)
    private boolean escalated = false;

    // Emails are copied so a case stays readable after an account is deleted
    // (loading a soft-deleted user through a relation would fail).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false, foreignKey = @ForeignKey(name = "fk_support_cases_requester"))
    private User requester;

    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", foreignKey = @ForeignKey(name = "fk_support_cases_assignee"))
    private User assignee;

    @Column(name = "assignee_email")
    private String assigneeEmail;

    // Context filled in by the server: always an event on ATTENDEE cases, order and ticket when known.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", foreignKey = @ForeignKey(name = "fk_support_cases_event"))
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_support_cases_order"))
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", foreignKey = @ForeignKey(name = "fk_support_cases_ticket"))
    private Ticket ticket;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Last message or status change, for sorting inboxes.
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
