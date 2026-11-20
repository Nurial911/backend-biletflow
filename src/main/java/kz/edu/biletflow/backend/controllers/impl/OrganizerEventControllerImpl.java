package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.OrganizerEventController;
import kz.edu.biletflow.backend.dtos.EventAttendanceResponse;
import kz.edu.biletflow.backend.dtos.EventHistoryEntryResponse;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.services.AttendanceService;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.EventPhase;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.EventHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrganizerEventControllerImpl implements OrganizerEventController {
    private final EventHistoryService eventHistoryService;
    private final AttendanceService attendanceService;

    @Override
    public ResponseEntity<Page<EventResponse>> getMyEvents(UserPrincipal currentUser, EventPhase phase, Pageable pageable) {
        return ResponseEntity.ok(eventHistoryService.getOrganizerEvents(currentUser.getId(), phase, pageable));
    }

    @Override
    public ResponseEntity<Page<EventHistoryEntryResponse>> getEventHistory(UserPrincipal currentUser, Long eventId,
                                                                           LocalDate from, LocalDate to,
                                                                           List<AuditAction> action, String entityType,
                                                                           Pageable pageable) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(eventHistoryService.getEventHistory(eventId, currentUser.getId(), isPlatformAdmin,
                from, to, action, entityType, pageable));
    }

    @Override
    public ResponseEntity<EventAttendanceResponse> getEventAttendance(UserPrincipal currentUser, Long eventId,
                                                                      Long ticketTypeId, Integer interval) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(attendanceService.getEventAttendance(eventId, currentUser.getId(), isPlatformAdmin,
                ticketTypeId, interval));
    }
}
