package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.CalendarLinksResponse;

// Calendar export (SRS 4.11). Visible to whoever can see the event, plus anyone holding a ticket for it,
// so a cancellation still reaches attendees after the event page is gone. Everyone else gets 404.
public interface CalendarService {
    record CalendarFile(String fileName, byte[] content) {
    }

    CalendarFile getEventCalendar(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin);

    CalendarLinksResponse getCalendarLinks(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin);
}
