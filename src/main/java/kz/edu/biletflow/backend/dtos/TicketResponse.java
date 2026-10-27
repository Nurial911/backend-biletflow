package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TicketResponse {
    private Long id;
    private String code;
    private TicketStatus status;
    private Long orderId;
    private Long eventId;
    private String eventTitle;
    private Long ticketTypeId;
    private String ticketTypeName;
    private LocalDateTime usedAt;
}
