package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping("/api/events")
public interface EventController {

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping()
    ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request,
                                              @AuthenticationPrincipal UserPrincipal currentUser);

    @GetMapping("/{id}")
    ResponseEntity<EventResponse> getEventById(@PathVariable Long id,
                                               @AuthenticationPrincipal UserPrincipal currentUser);

    @GetMapping
    ResponseEntity<Page<EventResponse>> getAllEvents(Pageable pageable,
                                                     @AuthenticationPrincipal UserPrincipal currentUser);

    @GetMapping("/organizer/{organizerId}")
    ResponseEntity<Page<EventResponse>> getEventsByOrganizer(
            @PathVariable Long organizerId,
            Pageable pageable,
            @AuthenticationPrincipal UserPrincipal currentUser
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

    // JPEG, PNG or WebP, at most 5 MB. Replaces the current poster.
    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping(value = "/{eventId}/poster", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<EventResponse> uploadPoster(@AuthenticationPrincipal UserPrincipal currentUser,
                                               @PathVariable Long eventId,
                                               @RequestParam("file") MultipartFile file);

    @PreAuthorize("hasRole('ORGANIZER')")
    @DeleteMapping("/{eventId}/poster")
    ResponseEntity<Void> deletePoster(@AuthenticationPrincipal UserPrincipal currentUser,
                                      @PathVariable Long eventId);

    @GetMapping("/{eventId}/poster")
    ResponseEntity<byte[]> getPoster(@PathVariable Long eventId,
                                     @AuthenticationPrincipal UserPrincipal currentUser);
}
