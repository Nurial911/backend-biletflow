package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.SeatMapResponse;
import kz.edu.biletflow.backend.dtos.SeatingLayoutResponse;
import kz.edu.biletflow.backend.dtos.SectionSeatsResponse;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.SeatingMode;

import java.util.List;

// Assigned seating (SRS 4.3.1): predefined layouts, an event's seat map, and its organizer setup.
// Selling seats happens in OrderServiceImpl: the unpaid order is the seat hold.
public interface SeatingService {
    List<SeatingLayoutResponse> getLayouts();

    // Organizer setup view (organizer of the event or PLATFORM_ADMIN; everyone else 404). Works in both modes.
    SeatMapResponse getSeating(Long eventId, Long requesterId, boolean isPlatformAdmin);

    SeatMapResponse setSeatingMode(Long organizerId, Long eventId, SeatingMode mode);

    SeatMapResponse assignSection(Long organizerId, Long eventId, Long sectionId, @Nullable Long ticketTypeId);

    SeatMapResponse setSeatsBlocked(Long organizerId, Long eventId, List<Long> seatIds, boolean blocked);

    // Public, visible like the event's ticket types.
    SeatMapResponse getSeatMap(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin);

    SectionSeatsResponse getSectionSeats(Long eventId, Long sectionId, @Nullable Long requesterId, boolean isPlatformAdmin);

    // Gives a duplicated event the original's seat map and blocked seats (not the section prices: ticket types
    // aren't copied). Runs inside the duplicate's transaction.
    void copySeating(Event original, Event copy);
}
