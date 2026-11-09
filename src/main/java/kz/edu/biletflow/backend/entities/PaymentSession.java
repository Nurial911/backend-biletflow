package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// One visit to the bank's payment page. Finished attempts are recorded as Payment rows.
@Entity
@Table(name = "payment_sessions", indexes = {
        @Index(name = "idx_payment_sessions_status", columnList = "status, id"),
        @Index(name = "idx_payment_sessions_order", columnList = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_payment_sessions_order"))
    private Order order;

    // The bank's order number: unique per visit, 6 to 15 digits, unique in its last 6.
    @Column(name = "invoice_id", nullable = false, unique = true, updatable = false, length = 15)
    private String invoiceId;

    // Sent to the bank by the server only; it comes back on the result call to show the call is about our session.
    @Column(name = "secret_hash", nullable = false, updatable = false, length = 64)
    private String secretHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentSessionStatus status = PaymentSessionStatus.OPEN;

    // The bank's id of the successful attempt.
    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    // So each declined attempt on the page is recorded once.
    @Column(name = "last_declined_transaction_id", length = 64)
    private String lastDeclinedTransactionId;

    @Column(name = "release_reason", length = 500)
    private String releaseReason;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
