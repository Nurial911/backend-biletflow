package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.TicketResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService ticketService;

    @PostMapping("/{ticketId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TicketResponse> cancel(@AuthenticationPrincipal UserPrincipal user,
                                                  @PathVariable Long ticketId) {
        return ResponseEntity.ok(ticketService.cancel(user.getId(), ticketId));
    }
}
