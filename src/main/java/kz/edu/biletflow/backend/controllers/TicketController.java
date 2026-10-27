package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.TicketResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/tickets")
public interface TicketController {
    @GetMapping
    ResponseEntity<Page<TicketResponse>> getMyTickets(@AuthenticationPrincipal UserPrincipal currentUser,
                                                      Pageable pageable);

    @GetMapping("/{ticketId}")
    ResponseEntity<TicketResponse> getTicket(@AuthenticationPrincipal UserPrincipal currentUser,
                                             @PathVariable Long ticketId);

    // image/png. No "produces" here on purpose: errors must still be rendered as JSON.
    @GetMapping("/{ticketId}/qr")
    ResponseEntity<byte[]> getTicketQr(@AuthenticationPrincipal UserPrincipal currentUser,
                                       @PathVariable Long ticketId);
}
