package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.HoldPayoutRequest;
import kz.edu.biletflow.backend.dtos.PayoutResponse;
import kz.edu.biletflow.backend.entities.Payout;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// Simulated organizer payouts (SRS 3.2, 4.10). One per finished event that sold paid tickets, scheduled when it
// ends and paid PAYOUT_DELAY_HOURS later. No money moves: every payout has simulated=true.
public interface PayoutController {

    // The organizer's own payouts, latest first. ?status= filters.
    @PreAuthorize("hasRole('ORGANIZER')")
    @GetMapping("/api/organizer/payouts")
    ResponseEntity<Page<PayoutResponse>> getMyPayouts(@AuthenticationPrincipal UserPrincipal currentUser,
                                                      @RequestParam(required = false) Payout.Status status,
                                                      @PageableDefault(size = 20) Pageable pageable);

    // Organizer of the event or platform admin; others 404. 404 too until the event has ended.
    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @GetMapping("/api/organizer/events/{eventId}/payout")
    ResponseEntity<PayoutResponse> getEventPayout(@AuthenticationPrincipal UserPrincipal currentUser,
                                                  @PathVariable Long eventId);

    // q: event title, organizer email, reference, or the payout or event id.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/api/admin/payouts")
    ResponseEntity<Page<PayoutResponse>> searchPayouts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Payout.Status status,
            @RequestParam(required = false) Long organizerId,
            @RequestParam(required = false) Long eventId,
            @PageableDefault(size = 20, sort = "scheduledFor", direction = Sort.Direction.DESC) Pageable pageable);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/api/admin/payouts/{payoutId}")
    ResponseEntity<PayoutResponse> getPayout(@PathVariable Long payoutId);

    // SCHEDULED or automatically held payouts only. The organizer is told the reason.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/api/admin/payouts/{payoutId}/hold")
    ResponseEntity<PayoutResponse> holdPayout(@AuthenticationPrincipal UserPrincipal currentUser,
                                              @PathVariable Long payoutId,
                                              @Valid @RequestBody HoldPayoutRequest request);

    // Lifts an admin hold. A payout whose date has passed is paid at once (if nothing else blocks it).
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/api/admin/payouts/{payoutId}/release")
    ResponseEntity<PayoutResponse> releasePayout(@AuthenticationPrincipal UserPrincipal currentUser,
                                                 @PathVariable Long payoutId);

    // Pays now instead of on the scheduled date. If something blocks it (no payout account, suspended event or
    // organizer) the payout goes ON_HOLD with that reason; 400 while a refund is in progress or if it is held by an admin.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/api/admin/payouts/{payoutId}/pay-now")
    ResponseEntity<PayoutResponse> payNow(@AuthenticationPrincipal UserPrincipal currentUser,
                                          @PathVariable Long payoutId);

    // Payouts scheduled in the range. from/to: inclusive dates, default the last 30 days, at most 366 days.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/api/admin/reports/payouts.csv")
    ResponseEntity<byte[]> payoutsReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
