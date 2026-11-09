package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.edu.biletflow.backend.entities.PromoDiscountType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
public class CreatePromoCampaignRequest {
    @NotBlank(message = "Campaign name is required")
    @Size(max = 120, message = "Campaign name must be at most 120 characters long")
    private String name;

    @NotNull(message = "Discount type is required")
    private PromoDiscountType discountType;

    // Percent (at most 100) or KZT, depending on discountType.
    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Discount can have at most 2 decimal places")
    private BigDecimal discountValue;

    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    @Min(value = 1, message = "Maximum redemptions must be at least 1")
    private Integer maxRedemptions;

    // Leave empty for every ticket type of the event.
    private Set<Long> ticketTypeIds;
}
