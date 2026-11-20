package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.EventReportResponse;
import kz.edu.biletflow.backend.dtos.ReportEventRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

// Any signed-in user can report an event they can see (SRS 4.12); platform admins review reports
// under /api/admin/event-reports.
@RequestMapping("/api/events/{eventId}/reports")
public interface EventReportController {

    // 201. Not for the event's own organizer (400); one open report per user and event (409).
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    ResponseEntity<EventReportResponse> reportEvent(@AuthenticationPrincipal UserPrincipal currentUser,
                                                    @PathVariable Long eventId,
                                                    @Valid @RequestBody ReportEventRequest request);
}
