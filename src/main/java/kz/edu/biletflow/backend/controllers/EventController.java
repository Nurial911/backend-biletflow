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

    @PutMapping("/{eventId}")
    ResponseEntity<EventResponse> updateEvent(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventRequest request
    );

    @PostMapping("/{eventId}/publish")
    ResponseEntity<EventResponse> publishEvent(@AuthenticationPrincipal UserPrincipal currentUser,
                                               @PathVariable Long eventId);

    @PostMapping("/{eventId}/unpublish")
    ResponseEntity<EventResponse> unpublishEvent(@AuthenticationPrincipal UserPrincipal currentUser,
                                                 @PathVariable Long eventId);

    @PostMapping("/{eventId}/cancel")
    ResponseEntity<EventResponse> cancelEvent(@AuthenticationPrincipal UserPrincipal currentUser,
                                              @PathVariable Long eventId);

    @PostMapping("/{eventId}/duplicate")
    ResponseEntity<EventResponse> duplicateEvent(@AuthenticationPrincipal UserPrincipal currentUser,
                                                 @PathVariable Long eventId);
}
