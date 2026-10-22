package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.dtos.TicketTypeResponse;
import kz.edu.biletflow.backend.dtos.UpdateTicketTypeRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api")
public interface TicketTypeController {
    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/events/{eventId}/ticket-types")
    ResponseEntity<TicketTypeResponse> createTicketType(@AuthenticationPrincipal UserPrincipal currentUser,
                                                        @PathVariable Long eventId,
                                                        @Valid @RequestBody CreateTicketTypeRequest request);

    // Public: already covered by permitAll on GET /api/events/** in SecurityConfig
    @GetMapping("/events/{eventId}/ticket-types")
    ResponseEntity<List<TicketTypeResponse>> getTicketTypes(@PathVariable Long eventId,
                                                            @AuthenticationPrincipal UserPrincipal currentUser);

    @PreAuthorize("hasRole('ORGANIZER')")
    @PutMapping("/ticket-types/{ticketTypeId}")
    ResponseEntity<TicketTypeResponse> updateTicketType(@AuthenticationPrincipal UserPrincipal currentUser,
                                                        @PathVariable Long ticketTypeId,
                                                        @Valid @RequestBody UpdateTicketTypeRequest request);

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/ticket-types/{ticketTypeId}/hide")
    ResponseEntity<TicketTypeResponse> hideTicketType(@AuthenticationPrincipal UserPrincipal currentUser,
                                                      @PathVariable Long ticketTypeId);

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/ticket-types/{ticketTypeId}/unhide")
    ResponseEntity<TicketTypeResponse> unhideTicketType(@AuthenticationPrincipal UserPrincipal currentUser,
                                                        @PathVariable Long ticketTypeId);
}
