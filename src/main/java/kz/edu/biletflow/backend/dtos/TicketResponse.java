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
    // Assigned seating; all null for general admission. seatLabel reads e.g. "Stalls, row A, seat 5".
    private Long seatId;
    private String sectionName;
    private String rowLabel;
    private Integer seatNumber;
    private String seatLabel;
    private String attendeeEmail;
    private String attendeeName;
    private LocalDateTime usedAt;
}
