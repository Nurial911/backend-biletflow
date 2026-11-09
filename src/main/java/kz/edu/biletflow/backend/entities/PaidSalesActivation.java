package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// The record of one event's paid-sales activation (SRS 4.5). Demonstration data: no money moves.
@Entity
@Table(name = "paid_sales_activations")
@Getter
@Setter
@NoArgsConstructor
public class PaidSalesActivation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaidSalesStatus status = PaidSalesStatus.IN_PROGRESS;

    // KZT, the fee in force when it was paid. Null until then.
    @Column(name = "fee_amount", precision = 12, scale = 2)
    private BigDecimal feeAmount;

    // "SIM-<uuid>", so the record can't be mistaken for a real transaction reference.
    @Column(name = "fee_reference", unique = true)
    private String feeReference;

    @Column(name = "fee_paid_at")
    private LocalDateTime feePaidAt;

    @Column(name = "simulated", nullable = false)
    private boolean simulated = true;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_paid_sales_activations_event"))
    private Event event;
}
