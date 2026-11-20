package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PlatformSettingsResponse {
    // Values in effect now: the admin's setting, or the environment default when none was set.
    private BigDecimal activationFeeKzt;
    private BigDecimal processingFeePercent;
    // Environment defaults (app.payments.*), shown so the admin can see what they override.
    private BigDecimal defaultActivationFeeKzt;
    private BigDecimal defaultProcessingFeePercent;
    private LocalDateTime updatedAt; // null until an admin changes something
}
