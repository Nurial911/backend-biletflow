package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.EventAttendanceResponse;
import kz.edu.biletflow.backend.dtos.EventHistoryEntryResponse;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.entities.AuditAction;
import kz.edu.biletflow.backend.entities.EventPhase;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

// Organizer event history (SRS 4.16). Only the event's organizer or a platform admin gets data;
// anyone else, check-in staff included, gets 404.
@RequestMapping("/api/organizer/events")
public interface OrganizerEventController {

    // The organizer's own events, all statuses and suspended ones included; ?phase=UPCOMING|ACTIVE|COMPLETED|CANCELLED.
    @PreAuthorize("hasRole('ORGANIZER')")
    @GetMapping
    ResponseEntity<Page<EventResponse>> getMyEvents(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) EventPhase phase,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable);

    // Activity timeline, newest first. ?from=&to= are inclusive dates (yyyy-MM-dd); ?action= (repeatable)
    // and ?entityType= (EVENT, TICKET_TYPE, ORDER, PAYMENT, TICKET, PROMO_CAMPAIGN, SUPPORT_CASE, ...) filter by activity.
    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @GetMapping("/{eventId}/history")
    ResponseEntity<Page<EventHistoryEntryResponse>> getEventHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long eventId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<AuditAction> action,
            @RequestParam(required = false) String entityType,
            @PageableDefault(size = 50) Pageable pageable);

    // Attendance (SRS 4.15): ticket holders, checked in, absent, check-in rate, per ticket type, and arrivals
    // over time in ?interval= minutes (5, 10, 15, 30, 60, 120 or 1440; default 15). ?ticketTypeId= narrows everything.
    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @GetMapping("/{eventId}/attendance")
    ResponseEntity<EventAttendanceResponse> getEventAttendance(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long eventId,
            @RequestParam(required = false) Long ticketTypeId,
            @RequestParam(required = false) Integer interval);
}
