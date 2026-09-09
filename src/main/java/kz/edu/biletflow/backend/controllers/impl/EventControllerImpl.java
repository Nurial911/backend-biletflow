package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.EventController;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
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
}
