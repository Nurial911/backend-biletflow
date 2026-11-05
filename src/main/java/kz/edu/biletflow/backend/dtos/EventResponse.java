package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.VisibilityStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private String category;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationOpeningTime;
    private LocalDateTime registrationClosingTime;
    private Integer capacity;
    private VisibilityStatus visibilityStatus;
    private EventStatus status;
    private String posterUrl; // null when the event has no poster

    // Only owner/admin can see a suspended event, so these never reach the public.
    private boolean suspended;
    private LocalDateTime suspendedAt;
    private String suspensionReason;

    private Long organizerId;
    private VenueResponse venue;

}
