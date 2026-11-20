package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.CalendarLinksResponse;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.VisibilityStatus;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.TicketRepository;
import kz.edu.biletflow.backend.services.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class CalendarServiceImpl implements CalendarService {
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final EventCalendar eventCalendar;

    @Override
    @Transactional(readOnly = true)
    public CalendarFile getEventCalendar(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = getCalendarEvent(eventId, requesterId, isPlatformAdmin);
        return new CalendarFile(eventCalendar.fileName(event), eventCalendar.ics(event));
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarLinksResponse getCalendarLinks(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = getCalendarEvent(eventId, requesterId, isPlatformAdmin);
        boolean cancelled = event.getStatus() == EventStatus.CANCELLED;
        CalendarLinksResponse links = new CalendarLinksResponse();
        links.setEventId(event.getId());
        links.setCancelled(cancelled);
        links.setSequence(event.getCalendarSequence());
        UriComponentsBuilder base = RequestContextHolder.getRequestAttributes() != null
                ? ServletUriComponentsBuilder.fromCurrentContextPath()
                : UriComponentsBuilder.fromPath("");
        links.setIcsUrl(base.path("/api/events/{id}/calendar.ics").buildAndExpand(event.getId()).toUriString());
        // Web calendars can only add an entry, not cancel one: a cancelled event has just the file.
        if (!cancelled) {
            links.setGoogleCalendarUrl(eventCalendar.googleCalendarLink(event));
            links.setOutlookUrl(eventCalendar.outlookLink(event, "outlook.live.com"));
            links.setOffice365Url(eventCalendar.outlookLink(event, "outlook.office.com"));
        }
        return links;
    }

    // The event's own visibility rule (hidden existence: 404), widened to its ticket holders, whose
    // cancelled or suspended event is no longer public but who still have the entry in their calendar.
    private Event getCalendarEvent(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (isPlatformAdmin) {
            return event;
        }
        boolean isOwner = requesterId != null && event.getOrganizer().getId().equals(requesterId);
        boolean isPubliclyVisible = event.getStatus() == EventStatus.PUBLISHED
                && event.getVisibilityStatus() != VisibilityStatus.PRIVATE
                && !event.isSuspended();
        if (isOwner || isPubliclyVisible
                || requesterId != null && ticketRepository.existsByEventIdAndOwnerId(eventId, requesterId)) {
            return event;
        }
        throw new ResourceNotFoundException("Event not found with id: " + eventId);
    }
}
