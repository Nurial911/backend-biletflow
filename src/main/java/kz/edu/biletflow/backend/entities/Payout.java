package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A simulated payout of one finished event's sales to its organizer (SRS 3.2: payouts may be a clearly labelled
 * simulation). Created SCHEDULED when the event ends and paid once its date comes; the amount is recalculated
 * from the sales figures at that moment. No provider is called and no money moves.
 */
@Entity
@Table(name = "payouts")
@Getter
@Setter
@NoArgsConstructor
public class Payout {
    public enum Status {
        SCHEDULED,
        ON_HOLD,
        PAID,
        CANCELLED  // the event was cancelled, or nothing was left to pay
    }

    // ADMIN holds are released by an admin; the others clear by themselves once the cause is gone.
    public enum HoldType {
        ADMIN,
        NO_PAYOUT_ACCOUNT,
        EVENT_SUSPENDED,
        ORGANIZER_SUSPENDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false, updatable = false)
    private User organizer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_type", length = 30)
    private HoldType holdType;

    // Why it is ON_HOLD or CANCELLED; null otherwise.
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "gross_revenue", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossRevenue;

    @Column(name = "discounts", nullable = false, precision = 12, scale = 2)
    private BigDecimal discounts;

    @Column(name = "refunds", nullable = false, precision = 12, scale = 2)
    private BigDecimal refunds;

    @Column(name = "processing_fees", nullable = false, precision = 12, scale = 2)
    private BigDecimal processingFees;

    // Estimated while SCHEDULED or ON_HOLD, final once PAID.
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDateTime scheduledFor;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "reference", length = 80, unique = true)
    private String reference;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "iban_last4", length = 4)
    private String ibanLast4;

    @Column(name = "simulated", nullable = false)
    private boolean simulated = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
