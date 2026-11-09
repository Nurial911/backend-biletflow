package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.PromoDiscountType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

// A campaign as its organizer sees it. Figures come from orders; only PAID ones count as redemptions.
@Data
public class PromoCampaignResponse {
    private Long id;
    private Long eventId;
    private String name;
    private String code;
    // The link inside the Campaign QR code.
    private String campaignUrl;
    private PromoDiscountType discountType;
    private BigDecimal discountValue;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Integer maxRedemptions;
    private boolean enabled;
    private Set<Long> ticketTypeIds;
    private LocalDateTime createdAt;

    private long redemptions;
    // Unpaid orders still holding a redemption until their hold runs out.
    private long pendingRedemptions;
    // Null when the campaign has no limit.
    private Long remainingRedemptions;
    private long ticketsSold;
    private BigDecimal grossRevenue;
    private BigDecimal discountAmount;
    private BigDecimal netRevenue;
}
