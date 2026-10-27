package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.CheckInController;
import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.CheckInService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckInControllerImpl implements CheckInController {
    private final CheckInService checkInService;

    @Override
    public ResponseEntity<Page<EventResponse>> getAssignedEvents(UserPrincipal currentUser, Pageable pageable) {
        return ResponseEntity.ok(checkInService.getAssignedEvents(currentUser.getId(), pageable));
    }

    @Override
    public ResponseEntity<CheckInResponse> scan(UserPrincipal currentUser, Long eventId, ScanTicketRequest request) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(checkInService.scan(eventId, request.getCode(), currentUser.getId(), isPlatformAdmin));
    }

    @Override
    public ResponseEntity<TicketResponse> undo(UserPrincipal currentUser, Long eventId, Long ticketId) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(checkInService.undo(eventId, ticketId, currentUser.getId(), isPlatformAdmin));
    }

    @Override
    public ResponseEntity<Page<TicketResponse>> searchAttendees(UserPrincipal currentUser, Long eventId, String q, Pageable pageable) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(checkInService.searchAttendees(eventId, q, currentUser.getId(), isPlatformAdmin, pageable));
    }

    @Override
    public ResponseEntity<CheckInStatsResponse> getStats(UserPrincipal currentUser, Long eventId) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(checkInService.getStats(eventId, currentUser.getId(), isPlatformAdmin));
    }
}
