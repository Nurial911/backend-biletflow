package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Simulated payout destination (SRS 3.2). Only the last 4 IBAN characters are kept: nothing here can move money.
@Entity
@Table(name = "payout_accounts")
@Getter
@Setter
@NoArgsConstructor
public class PayoutAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_holder", nullable = false)
    private String accountHolder;

    @Column(name = "iban_last4", nullable = false, length = 4)
    private String ibanLast4;

    @Column(name = "simulated", nullable = false)
    private boolean simulated = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
