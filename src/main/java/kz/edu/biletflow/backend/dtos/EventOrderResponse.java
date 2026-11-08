package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// An order as the event's organizer sees it: includes who bought it.
@Data
public class EventOrderResponse {
    private Long id;
    private OrderStatus status;
    // Both null if the buyer has deleted their account (the order itself is kept).
    private Long buyerId;
    private String buyerEmail;
    private String attendeeName;
    private BigDecimal totalAmount;
    private int ticketCount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private List<OrderItemResponse> items;
}
