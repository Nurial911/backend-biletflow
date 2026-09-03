package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table(name = "organizer_profiles")
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "contact_information", columnDefinition = "TEXT")
    private String contactInformation;

    @Column(name = "verification_status")
    private String verificationStatus;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id", // Название колонки в БД
            referencedColumnName = "id",
            nullable = false,
            unique = true ) // Критически важно: гарантирует, что связь именно 1-к-1)
    private User user;

    @Column(name = "payout_account_id")
    private Long payoutAccountId;
}
