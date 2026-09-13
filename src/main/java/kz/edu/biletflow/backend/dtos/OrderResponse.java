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
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime reservationExpiresAt;
    private List<TicketResponse> tickets;
}
