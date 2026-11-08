package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One payment attempt on an order. Demonstration record: no provider is involved and no money moves.
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_order", columnList = "order_id"))
@Getter
@Setter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // "SIM-<uuid>", so the record can't be mistaken for a real transaction reference.
    @Column(name = "reference", nullable = false, unique = true, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    // KZT.
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    // KZT, deducted from the organizer's payout. 0 for a failed attempt.
    @Column(name = "processing_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal processingFee;

    @Column(name = "simulated", nullable = false)
    private boolean simulated = true;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payments_order"))
    private Order order;
}
