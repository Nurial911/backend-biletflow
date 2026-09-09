package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/events")
public interface EventController {

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping()
    ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request,
                                              @AuthenticationPrincipal UserPrincipal currentUser);

    @GetMapping("/{id}")
    ResponseEntity<EventResponse> getEventById(@PathVariable Long id);

    @GetMapping
    ResponseEntity<Page<EventResponse>> getAllEvents(Pageable pageable);

    @GetMapping("/organizer/{organizerId}")
    ResponseEntity<Page<EventResponse>> getEventsByOrganizer(
            @PathVariable Long organizerId,
            Pageable pageable
    );

    @PutMapping("/{organizerId}/{eventId}")
    ResponseEntity<EventResponse> updateEvent(
            @PathVariable Long organizerId,
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventRequest request
    );

    @PostMapping("/{organizerId}/{eventId}/publish")
    ResponseEntity<EventResponse> publishEvent(@PathVariable Long organizerId,
                                               @PathVariable Long eventId);

    @PostMapping("/{organizerId}/{eventId}/unpublish")
    ResponseEntity<EventResponse> unpublishEvent(@PathVariable Long organizerId,
                                                 @PathVariable Long eventId);

    @PostMapping("/{organizerId}/{eventId}/cancel")
    ResponseEntity<EventResponse> cancelEvent(@PathVariable Long organizerId,
                                              @PathVariable Long eventId);

    @PostMapping("/{organizerId}/{eventId}/duplicate")
    ResponseEntity<EventResponse> duplicateEvent(@PathVariable Long organizerId,
                                                 @PathVariable Long eventId);
}
