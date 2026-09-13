package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.dtos.TicketTypeResponse;
import kz.edu.biletflow.backend.dtos.UpdateTicketTypeRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.TicketTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TicketTypeControllerImpl implements TicketTypeController {
    private final TicketTypeService ticketTypeService;

    @Override
    public ResponseEntity<TicketTypeResponse> create(UserPrincipal user, Long eventId, CreateTicketTypeRequest request) {
        return ResponseEntity.ok(ticketTypeService.create(user.getId(), eventId, request));
    }

    @Override
    public ResponseEntity<TicketTypeResponse> update(UserPrincipal user, Long ticketTypeId, UpdateTicketTypeRequest request) {
        return ResponseEntity.ok(ticketTypeService.update(user.getId(), ticketTypeId, request));
    }

    @Override
    public ResponseEntity<Page<TicketTypeResponse>> list(Long eventId, Pageable pageable) {
        return ResponseEntity.ok(ticketTypeService.list(eventId, pageable));
    }
}
