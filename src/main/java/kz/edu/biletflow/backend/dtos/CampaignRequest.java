package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import kz.edu.biletflow.backend.entities.DiscountType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CampaignRequest {
    @NotNull private DiscountType discountType;
    @NotNull @DecimalMin("0.00") private BigDecimal discountValue;
    private LocalDateTime validityStart;
    private LocalDateTime validityEnd;
    private Integer maxRedemptions;
    private String applicableTicketTypes;
    @NotNull private String code;
}
