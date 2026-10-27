package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// Used by the ticket-verification app. A scan always answers 200 with a CheckInResult;
// 4xx means the request itself was not allowed (not staff of the event, bad body, ...).
@RequestMapping("/api/check-in")
@PreAuthorize("hasAnyRole('EVENT_ADMIN', 'ORGANIZER', 'PLATFORM_ADMIN')")
public interface CheckInController {
    // Events the current EVENT_ADMIN is assigned to.
    @GetMapping("/events")
    ResponseEntity<Page<EventResponse>> getAssignedEvents(@AuthenticationPrincipal UserPrincipal currentUser,
                                                          Pageable pageable);

    @PostMapping("/events/{eventId}/scan")
    ResponseEntity<CheckInResponse> scan(@AuthenticationPrincipal UserPrincipal currentUser,
                                         @PathVariable Long eventId,
                                         @Valid @RequestBody ScanTicketRequest request);

    @PostMapping("/events/{eventId}/tickets/{ticketId}/undo")
    ResponseEntity<TicketResponse> undo(@AuthenticationPrincipal UserPrincipal currentUser,
                                         @PathVariable Long eventId,
                                         @PathVariable Long ticketId);

    // Manual attendee search: q matches part of the email or the exact ticket code.
    @GetMapping("/events/{eventId}/attendees")
    ResponseEntity<Page<TicketResponse>> searchAttendees(@AuthenticationPrincipal UserPrincipal currentUser,
                                                         @PathVariable Long eventId,
                                                         @RequestParam(required = false) String q,
                                                         Pageable pageable);

    @GetMapping("/events/{eventId}/stats")
    ResponseEntity<CheckInStatsResponse> getStats(@AuthenticationPrincipal UserPrincipal currentUser,
                                                  @PathVariable Long eventId);
}
