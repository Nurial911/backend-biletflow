package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.EventController;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.services.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class EventControllerImpl implements EventController {
    private final EventService eventService;

    @Override
    public ResponseEntity<EventResponse> createEvent(Long organizerId, CreateEventRequest request) {
        EventResponse createdEvent = eventService.createEvent(organizerId, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/events/{id}")
                .buildAndExpand(createdEvent.getId())
                .toUri();
        return ResponseEntity.created(location).body(createdEvent);
    }

    @Override
    public ResponseEntity<EventResponse> getEventById(Long id) {
        return ResponseEntity.ok(eventService.getEventById(id));
    }

    @Override
    public ResponseEntity<Page<EventResponse>> getAllEvents(Pageable pageable) {
        return ResponseEntity.ok(eventService.getAllEvents(pageable));
    }

    @Override
    public ResponseEntity<Page<EventResponse>> getEventsByOrganizer(Long organizerId, Pageable pageable) {
        return ResponseEntity.ok(eventService.getEventsByOrganizer(organizerId, pageable));
    }

    @Override
    public ResponseEntity<EventResponse> updateEvent(Long organizerId, Long eventId, UpdateEventRequest request) {
        return ResponseEntity.ok(eventService.updateEvent(organizerId, eventId, request));
    }

    @Override
    public ResponseEntity<EventResponse> publishEvent(Long organizerId, Long eventId) {
        return ResponseEntity.ok(eventService.publishEvent(organizerId, eventId));
    }

    @Override
    public ResponseEntity<EventResponse> unpublishEvent(Long organizerId, Long eventId) {
        return ResponseEntity.ok(eventService.unpublishEvent(organizerId, eventId));
    }

    @Override
    public ResponseEntity<EventResponse> cancelEvent(Long organizerId, Long eventId) {
        return ResponseEntity.ok(eventService.cancelEvent(organizerId, eventId));
    }

    @Override
    public ResponseEntity<EventResponse> duplicateEvent(Long organizerId, Long eventId) {
        EventResponse duplicatedEvent = eventService.duplicateEvent(organizerId, eventId);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/events/{id}")
                .buildAndExpand(duplicatedEvent.getId())
                .toUri();
        return ResponseEntity.created(location).body(duplicatedEvent);

    }
}
