package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;

import java.math.BigDecimal;

// Partial update: a null field is left unchanged.
@Data
public class UpdatePlatformSettingsRequest {
    // Charged for activating paid sales on an event; already-paid fees keep their amount.
    @DecimalMin(value = "0", message = "Activation fee must not be negative")
    @DecimalMax(value = "1000000", message = "Activation fee must be at most 1 000 000 KZT")
    @Digits(integer = 7, fraction = 2, message = "Activation fee has at most 2 decimal places")
    private BigDecimal activationFeeKzt;

    // Deducted from the organizer's payout; each payment keeps the fee it was charged.
    @DecimalMin(value = "0", message = "Processing fee must not be negative")
    @DecimalMax(value = "50", message = "Processing fee must be at most 50%")
    @Digits(integer = 2, fraction = 2, message = "Processing fee has at most 2 decimal places")
    private BigDecimal processingFeePercent;
}
