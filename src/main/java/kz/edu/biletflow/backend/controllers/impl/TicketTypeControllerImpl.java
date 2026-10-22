package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.TicketTypeController;
import kz.edu.biletflow.backend.dtos.CreateTicketTypeRequest;
import kz.edu.biletflow.backend.dtos.TicketTypeResponse;
import kz.edu.biletflow.backend.dtos.UpdateTicketTypeRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.TicketTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TicketTypeControllerImpl implements TicketTypeController {
    private final TicketTypeService ticketTypeService;

    @Override
    public ResponseEntity<TicketTypeResponse> createTicketType(UserPrincipal currentUser, Long eventId, CreateTicketTypeRequest request) {
        TicketTypeResponse created = ticketTypeService.createTicketType(currentUser.getId(), eventId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<List<TicketTypeResponse>> getTicketTypes(Long eventId, UserPrincipal currentUser) {
        Long requesterId = currentUser != null ? currentUser.getId() : null;
        boolean isPlatformAdmin = currentUser != null && currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(ticketTypeService.getTicketTypesForEvent(eventId, requesterId, isPlatformAdmin));

    }

    @Override
    public ResponseEntity<TicketTypeResponse> updateTicketType(UserPrincipal currentUser, Long ticketTypeId, UpdateTicketTypeRequest request) {
        return ResponseEntity.ok(ticketTypeService.updateTicketType(currentUser.getId(), ticketTypeId, request));
    }

    @Override
    public ResponseEntity<TicketTypeResponse> hideTicketType(UserPrincipal currentUser, Long ticketTypeId) {
        return ResponseEntity.ok(ticketTypeService.setHidden(currentUser.getId(), ticketTypeId, true));
    }

    @Override
    public ResponseEntity<TicketTypeResponse> unhideTicketType(UserPrincipal currentUser, Long ticketTypeId) {
        return ResponseEntity.ok(ticketTypeService.setHidden(currentUser.getId(), ticketTypeId, false));
    }
}
