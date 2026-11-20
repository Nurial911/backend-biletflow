package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// One row of the platform admin's order search.
@Data
public class AdminOrderResponse {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private OrderStatus status;
    private Long buyerId;
    private String buyerEmail;
    private String attendeeName;
    private int ticketCount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String promoCode; // null without a promo code
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
}
