package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * An in-app notification that is also the queue entry for its email. Written in the transaction of
 * the action it reports, so it exists exactly when the action committed; a scheduler sends the email.
 * Never put sign-in or reset links here: those are mailed directly and are not stored.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user", columnList = "user_id, id"),
        @Index(name = "idx_notifications_email_due", columnList = "email_status, next_attempt_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Plain value and address copy: the entry stays deliverable and readable whatever happens to the account.
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "recipient_email", nullable = false, updatable = false)
    private String recipientEmail;

    @Column(name = "type", nullable = false, updatable = false, length = 40)
    private String type;

    @Column(name = "title", nullable = false, updatable = false, length = 300)
    private String title;

    @Column(name = "body", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;

    // Set on ORDER_CONFIRMED: the email then carries the order's tickets as a PDF.
    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false)
    private EmailDeliveryStatus emailStatus = EmailDeliveryStatus.PENDING;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
