package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.EventOrderResponse;
import kz.edu.biletflow.backend.dtos.EventSalesResponse;
import kz.edu.biletflow.backend.dtos.SalesSummaryResponse;
import kz.edu.biletflow.backend.entities.OrderStatus;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// Sales dashboard of an organizer. Only the event's organizer (or a platform admin for the
// per-event endpoints) gets data; anyone else gets 404 so other organizers' events stay hidden.
@RequestMapping("/api/organizer")
public interface OrganizerSalesController {

    // All own events (newest start first), or only ?eventIds=1,2,3; plus totals over them.
    @PreAuthorize("hasRole('ORGANIZER')")
    @GetMapping("/sales/events")
    ResponseEntity<SalesSummaryResponse> getSalesSummary(@AuthenticationPrincipal UserPrincipal currentUser,
                                                         @RequestParam(required = false) List<Long> eventIds);

    // One event, including sales by day.
    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @GetMapping("/events/{eventId}/sales")
    ResponseEntity<EventSalesResponse> getEventSales(@AuthenticationPrincipal UserPrincipal currentUser,
                                                     @PathVariable Long eventId);

    // Orders of one event, newest first. ?status=PAID filters by status, ?q= by part of the buyer's email.
    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @GetMapping("/events/{eventId}/orders")
    ResponseEntity<Page<EventOrderResponse>> getEventOrders(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long eventId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);
}
