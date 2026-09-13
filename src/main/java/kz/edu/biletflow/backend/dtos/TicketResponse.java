package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.TicketStatus;
import lombok.Data;

@Data
public class TicketResponse {
    private Long id;
    private String ticketIdentifier;
    private Long eventId;
    private Long ticketTypeId;
    private TicketStatus status;
}
