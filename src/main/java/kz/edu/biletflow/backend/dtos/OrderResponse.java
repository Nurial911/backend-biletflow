package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponse {
    private Long id;
    private Long eventId;
    private String eventTitle;
    private OrderStatus status;
    private String attendeeName;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String promoCode; // null without a promo code
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private List<OrderItemResponse> items;
}
