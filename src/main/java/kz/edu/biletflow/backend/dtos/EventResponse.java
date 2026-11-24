package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.EventPhase;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.VisibilityStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventResponse {
    // GENERAL_ADMISSION or ASSIGNED; with ASSIGNED, GET /api/events/{id}/seat-map has the seats.
    private kz.edu.biletflow.backend.entities.SeatingMode seatingMode;
    private Long id;
    private String title;
    private String description;
    private String category;
    private String refundPolicy; // the organizer's own terms, null if none
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationOpeningTime;
    private LocalDateTime registrationClosingTime;
    private Integer capacity;
    private VisibilityStatus visibilityStatus;
    private EventStatus status;
    // UPCOMING / ACTIVE / COMPLETED / CANCELLED by the clock (EventPhase), for the organizer dashboard.
    private EventPhase phase;
    private String posterUrl; // null when the event has no poster
    // Lowest price of a visible ticket type in KZT ("from 5000 KZT"); 0 = has free tickets; null = no ticket types yet.
    // Ignores sales windows and sold-out types: it is the price list, not what can be bought right now.
    private BigDecimal minPrice;
    // False: paid ticket types are shown but can't be bought yet. Free ones are unaffected.
    private boolean paidSalesActive;

    // Only owner/admin can see a suspended event, so these never reach the public.
    private boolean suspended;
    private LocalDateTime suspendedAt;
    private String suspensionReason;

    // The organizer's USER id (User.id, as in /api/users/{id}), not OrganizerProfile.id.
    private Long organizerId;
    private VenueResponse venue;

}
