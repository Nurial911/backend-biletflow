package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.PromoDiscountType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

// What the event page may show about a code. Empty ticketTypeIds = every ticket type.
@Data
public class PromoPreviewResponse {
    private String code;
    private PromoDiscountType discountType;
    private BigDecimal discountValue;
    private LocalDateTime validUntil;
    private Set<Long> ticketTypeIds;
}
