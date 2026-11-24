package kz.edu.biletflow.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

// Sales dashboard of an organizer. Only the event's organizer (or a platform admin for the
// per-event endpoints) gets data; anyone else gets 404 so other organizers' events stay hidden.
@Tag(name = "Organizer sales", description = """
        Sales dashboard. Money is KZT; times are Asia/Almaty local time without an offset.
        Counting rules: ticketsSold = tickets of PAID orders (free ones included, with 0 revenue);
        ticketsReserved = tickets of PENDING orders that haven't expired; ticketsRefunded = tickets of REFUNDED orders
        (back on sale). revenue is gross: PAID + REFUNDED orders at the price of the moment of sale, before discounts.
        netRevenue = revenue - discounts - refunds; estimatedPayout = netRevenue - processingFees (simulated).
        Other organizers' events answer 404, never 403.""")
@RequestMapping("/api/organizer")
public interface OrganizerSalesController {

    // All own events (newest start first), or only ?eventIds=1,2,3; plus totals over them.
    @Operation(summary = "Sales of the organizer's events, with totals",
            description = "All own events, newest start first, and `totals` summed over the returned events. "
                    + "No date filter: every figure covers each event's whole history.")
    @PreAuthorize("hasRole('ORGANIZER')")
    @GetMapping("/sales/events")
    ResponseEntity<SalesSummaryResponse> getSalesSummary(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Parameter(description = "Only these events (comma-separated ids). An id that isn't one of the "
                    + "organizer's events makes the whole request 404.", example = "12,15")
            @RequestParam(required = false) List<Long> eventIds);

    // One event, including sales by day. ?from=&to= (inclusive dates) and ?ticketTypeId= filter salesByDay only;
    // the totals and ticketTypes always cover the whole event.
    @Operation(summary = "Sales of one event, with sales by day",
            description = """
                    The organizer of the event or a platform admin.

                    **The filters narrow only `salesByDay`.** Every other figure (orders, tickets, revenue,                     discounts, refunds, fees, payout, `ticketTypes`) always covers the whole event, so the totals                     don't change when a period or ticket type is picked.

                    `salesByDay` groups PAID and REFUNDED orders by the day they were **paid** (Asia/Almaty),                     refunded ones included, since the sale happened on that day. Only days with sales are listed,                     oldest first: fill the gaps with zeros on the chart. Revenue there is gross, like `revenue`.

                    400 when `from` is after `to`; 404 for a ticket type of another event.""")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @GetMapping("/events/{eventId}/sales")
    ResponseEntity<EventSalesResponse> getEventSales(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long eventId,
            @Parameter(description = "First day, inclusive (`YYYY-MM-DD`, from 00:00 Asia/Almaty). Empty = from the first sale.",
                    example = "2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day, inclusive (`YYYY-MM-DD`, up to 24:00 Asia/Almaty). Empty = up to now.",
                    example = "2026-11-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Only this ticket type's tickets and revenue in `salesByDay` "
                    + "(the order count then means orders containing it).")
            @RequestParam(required = false) Long ticketTypeId);

    // Orders of one event, newest first. ?status=PAID filters by status, ?q= by part of the buyer's email.
    @Operation(summary = "Orders of one event", description = "Newest first by default; the organizer of the event or a platform admin.")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @GetMapping("/events/{eventId}/orders")
    ResponseEntity<Page<EventOrderResponse>> getEventOrders(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long eventId,
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Part of the buyer's email, case-insensitive; `%` and `_` are literal.")
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);
}
