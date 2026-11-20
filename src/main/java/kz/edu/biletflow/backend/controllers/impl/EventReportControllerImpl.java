package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.EventReportController;
import kz.edu.biletflow.backend.dtos.EventReportResponse;
import kz.edu.biletflow.backend.dtos.ReportEventRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.EventReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class EventReportControllerImpl implements EventReportController {
    private final EventReportService eventReportService;

    @Override
    public ResponseEntity<EventReportResponse> reportEvent(UserPrincipal currentUser, Long eventId,
                                                           ReportEventRequest request) {
        EventReportResponse report = eventReportService.reportEvent(eventId, currentUser.getId(),
                currentUser.hasRole("PLATFORM_ADMIN"), request);
        // The report is read back by admins only.
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/admin/event-reports/{id}").buildAndExpand(report.getId()).toUri();
        return ResponseEntity.created(location).body(report);
    }
}
