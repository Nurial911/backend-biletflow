package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/events")
public interface EventController {

    @PostMapping("/{organizerId}")
    ResponseEntity<EventResponse> createEvent(@PathVariable Long organizerId,
                                              @Valid @RequestBody CreateEventRequest request);

    @GetMapping("/{id}")
    ResponseEntity<EventResponse> getEventById(@PathVariable Long id);

    @GetMapping
    ResponseEntity<Page<EventResponse>> getAllEvents(Pageable pageable);

    @GetMapping("/organizer/{organizerId}")
    ResponseEntity<Page<EventResponse>> getEventsByOrganizer(
            @PathVariable Long organizerId,
            Pageable pageable
    );
}
