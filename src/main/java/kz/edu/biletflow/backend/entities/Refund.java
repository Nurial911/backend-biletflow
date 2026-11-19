package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Money returned for a paid order. Orders are refunded whole (SRS 4.9 asks for full refunds only).
// At most one PENDING or SUCCEEDED refund per order: a partial unique index in V2__refunds.sql.
@Entity
@Table(name = "refunds", indexes = @Index(name = "idx_refunds_order", columnList = "order_id"))
@Getter
@Setter
@NoArgsConstructor
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_refunds_order"))
    private Order order;

    // The payment being returned. Null only for orders that never had a payment record (demo data).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", updatable = false, foreignKey = @ForeignKey(name = "fk_refunds_payment"))
    private Payment payment;

    // "SIM-REFUND-<uuid>" or "HALYK-REFUND-<uuid>".
    @Column(name = "reference", nullable = false, unique = true, updatable = false, length = 80)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    // KZT: the order's total after discount, i.e. what the buyer paid.
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // SIMULATED or HALYK, the same as the payment it returns.
    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "simulated", nullable = false)
    private boolean simulated = true;

    // Why the organizer refunded, shown to the buyer.
    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // Organizer or platform admin. A plain id, so the record stays readable after a user is soft-deleted.
    @Column(name = "initiated_by_id", nullable = false, updatable = false)
    private Long initiatedById;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
