package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TicketTypeResponse {
    private Long id;
    private Long eventId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer quantity;
    private long sold;          // paid + currently reserved by unpaid orders
    private long available;
    private LocalDateTime salesStart;
    private LocalDateTime salesEnd;
    private Integer maxPerOrder;
    private boolean hidden;
}