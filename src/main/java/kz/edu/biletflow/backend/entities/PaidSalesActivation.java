package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "paid_sales_activations")
@Getter
@Setter
@NoArgsConstructor
public class PaidSalesActivation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_account_id")
    private PayoutAccount payoutAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivationStatus status = ActivationStatus.PENDING;

    @Column(name = "activation_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal activationFee = BigDecimal.ZERO;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
}
