package kz.edu.biletflow.backend.entities;

import java.time.LocalDateTime;

/**
 * Where an event stands in time on the organizer dashboard (SRS 4.16). Derived from the clock, never
 * stored. Independent of the publication status: an unpublished draft can still be UPCOMING.
 * An event without an end time is treated as lasting until the end of its start day.
 */
public enum EventPhase {
    UPCOMING,
    ACTIVE,
    COMPLETED,
    CANCELLED;

    public static EventPhase of(Event event, LocalDateTime now) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return CANCELLED;
        }
        if (now.isBefore(event.getStartTime())) {
            return UPCOMING;
        }
        return now.isBefore(effectiveEnd(event)) ? ACTIVE : COMPLETED;
    }

    public static LocalDateTime effectiveEnd(Event event) {
        return event.getEndTime() != null
                ? event.getEndTime()
                : event.getStartTime().toLocalDate().plusDays(1).atStartOfDay();
    }
}
