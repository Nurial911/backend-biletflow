package kz.edu.biletflow.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// The single row of settings a platform admin can change at runtime (SRS 4.12).
// A null value means the default from the environment (app.payments.*) applies.
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
public class PlatformSettings {
    public static final long ID = 1L;

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "activation_fee_kzt", precision = 12, scale = 2)
    private BigDecimal activationFeeKzt;

    @Column(name = "processing_fee_percent", precision = 5, scale = 2)
    private BigDecimal processingFeePercent;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_id")
    private Long updatedById;
}
