package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.AssignSectionRequest;
import kz.edu.biletflow.backend.dtos.BlockSeatsRequest;
import kz.edu.biletflow.backend.dtos.SeatMapResponse;
import kz.edu.biletflow.backend.dtos.SectionSeatsResponse;
import kz.edu.biletflow.backend.dtos.SetSeatingModeRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// Assigned seating (SRS 4.3.1). Seats are bought through POST /api/orders with "seatIds".
@RequestMapping("/api/events/{eventId}")
public interface SeatingController {
    // Organizer setup view; public at the filter level (GET /api/events/**), so the service answers 404 to anyone
    // but the organizer and platform admins.
    @GetMapping("/seating")
    ResponseEntity<SeatMapResponse> getSeating(@AuthenticationPrincipal UserPrincipal currentUser,
                                               @PathVariable Long eventId);

    // GENERAL_ADMISSION or ASSIGNED (the venue's seat map); only while the event has no orders.
    @PreAuthorize("hasRole('ORGANIZER')")
    @PutMapping("/seating")
    ResponseEntity<SeatMapResponse> setSeatingMode(@AuthenticationPrincipal UserPrincipal currentUser,
                                                   @PathVariable Long eventId,
                                                   @Valid @RequestBody SetSeatingModeRequest request);

    // Links a section to a ticket type (its price category), or takes it off sale with ticketTypeId null.
    @PreAuthorize("hasRole('ORGANIZER')")
    @PutMapping("/seating/sections/{sectionId}")
    ResponseEntity<SeatMapResponse> assignSection(@AuthenticationPrincipal UserPrincipal currentUser,
                                                  @PathVariable Long eventId,
                                                  @PathVariable Long sectionId,
                                                  @Valid @RequestBody AssignSectionRequest request);

    @PreAuthorize("hasRole('ORGANIZER')")
    @PutMapping("/seating/seats")
    ResponseEntity<SeatMapResponse> setSeatsBlocked(@AuthenticationPrincipal UserPrincipal currentUser,
                                                    @PathVariable Long eventId,
                                                    @Valid @RequestBody BlockSeatsRequest request);

    // Public: sections with prices and availability.
    @GetMapping("/seat-map")
    ResponseEntity<SeatMapResponse> getSeatMap(@AuthenticationPrincipal UserPrincipal currentUser,
                                               @PathVariable Long eventId);

    // Public: the seats of one section with their status; the client polls it to stay current.
    @GetMapping("/seat-map/sections/{sectionId}")
    ResponseEntity<SectionSeatsResponse> getSectionSeats(@AuthenticationPrincipal UserPrincipal currentUser,
                                                         @PathVariable Long eventId,
                                                         @PathVariable Long sectionId);
}
