package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.CalendarLinksResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

// Calendar export (SRS 4.11). Public GETs: anyone who can see the event, plus its ticket holders
// (so a cancelled event's file still reaches them); everyone else 404.
@RequestMapping("/api/events/{eventId}")
public interface CalendarController {

    // iCalendar file (text/calendar). Same UID for the event's lifetime; a cancelled event's file has
    // STATUS:CANCELLED and a higher SEQUENCE, so it replaces the entry added earlier.
    @GetMapping("/calendar.ics")
    ResponseEntity<byte[]> getEventCalendar(@PathVariable Long eventId,
                                            @AuthenticationPrincipal UserPrincipal currentUser);

    // The .ics URL plus Google Calendar and Outlook "add event" links (none of these for a cancelled event).
    @GetMapping("/calendar")
    ResponseEntity<CalendarLinksResponse> getCalendarLinks(@PathVariable Long eventId,
                                                           @AuthenticationPrincipal UserPrincipal currentUser);
}
