package kz.edu.biletflow.backend.services;

import com.sun.jdi.request.EventRequest;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventService {
    EventResponse createEvent(Long organizerId, CreateEventRequest eventRequest);

    EventResponse getEventById(Long id);

    Page<EventResponse> getAllEvents(Pageable pageable);

    Page<EventResponse> getEventsByOrganizer(Long organizerId, Pageable pageable);

    EventResponse updateEvent(Long organizerId, Long eventId, UpdateEventRequest request);

    EventResponse publishEvent(Long organizerId, Long eventId);

    EventResponse unpublishEvent(Long organizerId, Long eventId);

    EventResponse cancelEvent(Long organizerId, Long eventId);

    EventResponse duplicateEvent(Long organizerId, Long eventId);

}
