package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.TicketController;
import kz.edu.biletflow.backend.dtos.TicketResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TicketControllerImpl implements TicketController {
    private final TicketService ticketService;

    @Override
    public ResponseEntity<Page<TicketResponse>> getMyTickets(UserPrincipal currentUser, Pageable pageable) {
        return ResponseEntity.ok(ticketService.getMyTickets(currentUser.getId(), pageable));
    }

    @Override
    public ResponseEntity<TicketResponse> getTicket(UserPrincipal currentUser, Long ticketId) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(ticketService.getTicketById(ticketId, currentUser.getId(), isPlatformAdmin));
    }
}
