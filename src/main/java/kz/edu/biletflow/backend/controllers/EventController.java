package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateEventRequest;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.SuspendEventRequest;
import kz.edu.biletflow.backend.dtos.UpdateEventRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

@RequestMapping("/api/events")
public interface EventController {

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping()
    ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request,
                                              @AuthenticationPrincipal UserPrincipal currentUser);

    @GetMapping("/{id}")
    ResponseEntity<EventResponse> getEventById(@PathVariable Long id,
                                               @AuthenticationPrincipal UserPrincipal currentUser);

    // The catalog. Filters are optional and combine with AND (see EventCatalogFilter); sort with
    // e.g. sort=startTime,asc or sort=minPrice,asc. Anonymous and attendees see PUBLISHED + PUBLIC events,
    // an organizer also their own, a PLATFORM_ADMIN everything.
    @GetMapping
    ResponseEntity<Page<EventResponse>> getAllEvents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BigDecimal priceMin,
            @RequestParam(required = false) BigDecimal priceMax,
            Pageable pageable,
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

    // Moderation: hides the event from everyone but owner/admin, stops sales, cancels unpaid orders.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/{eventId}/suspend")
    ResponseEntity<EventResponse> suspendEvent(@AuthenticationPrincipal UserPrincipal currentUser,
                                               @PathVariable Long eventId,
                                               @Valid @RequestBody(required = false) SuspendEventRequest request);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/{eventId}/unsuspend")
    ResponseEntity<EventResponse> unsuspendEvent(@AuthenticationPrincipal UserPrincipal currentUser,
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
