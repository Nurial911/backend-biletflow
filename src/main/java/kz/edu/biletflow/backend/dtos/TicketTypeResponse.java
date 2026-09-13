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
    private LocalDateTime salesStartTime;
    private LocalDateTime salesEndTime;
    private Integer limitPerOrder;
    private boolean hidden;
}
