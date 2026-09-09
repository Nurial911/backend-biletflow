package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.EventController;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.services.EventService;
import lombok.RequiredArgsConstructor;
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
}
