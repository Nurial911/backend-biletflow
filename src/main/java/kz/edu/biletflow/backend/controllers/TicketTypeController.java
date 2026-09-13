package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.dtos.TicketTypeResponse;
import kz.edu.biletflow.backend.dtos.UpdateTicketTypeRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/events/{eventId}/ticket-types")
public interface TicketTypeController {
    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    ResponseEntity<TicketTypeResponse> create(@AuthenticationPrincipal UserPrincipal user,
                                               @PathVariable Long eventId,
                                               @Valid @RequestBody CreateTicketTypeRequest request);

    @PutMapping("/{ticketTypeId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    ResponseEntity<TicketTypeResponse> update(@AuthenticationPrincipal UserPrincipal user,
                                               @PathVariable Long ticketTypeId,
                                               @Valid @RequestBody UpdateTicketTypeRequest request);

    @GetMapping
    ResponseEntity<Page<TicketTypeResponse>> list(@PathVariable Long eventId, Pageable pageable);
}
