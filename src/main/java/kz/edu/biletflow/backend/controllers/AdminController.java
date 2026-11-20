package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.AdminOrderResponse;
import kz.edu.biletflow.backend.dtos.AdminOverviewResponse;
import kz.edu.biletflow.backend.dtos.AdminPaymentResponse;
import kz.edu.biletflow.backend.dtos.AdminRefundResponse;
import kz.edu.biletflow.backend.dtos.EventReportResponse;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.dtos.PlatformSettingsResponse;
import kz.edu.biletflow.backend.dtos.PromoCampaignResponse;
import kz.edu.biletflow.backend.dtos.ReviewEventReportRequest;
import kz.edu.biletflow.backend.dtos.UpdatePlatformSettingsRequest;
import kz.edu.biletflow.backend.entities.EventReport;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.OrderStatus;
import kz.edu.biletflow.backend.entities.PaymentStatus;
import kz.edu.biletflow.backend.entities.RefundStatus;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// Platform administration (SRS 4.12): search across all organizers, monitoring, reported events,
// platform settings and CSV reports. PLATFORM_ADMIN only. User search and suspension live under /api/users,
// event suspension under /api/events/{id}/suspend, paid-sales records under /api/admin/paid-sales-activations.
// In searches, q is case-insensitive and literal, and also matches an exact id when it is a number.
@RequestMapping("/api/admin")
public interface AdminController {

    // Counts of what needs attention: suspensions, open reports, failed payments and refunds, escalated cases.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/overview")
    ResponseEntity<AdminOverviewResponse> getOverview();

    // All events, drafts/private/suspended included. q: title, organizer email or event id.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/events")
    ResponseEntity<Page<EventResponse>> searchEvents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Boolean suspended,
            @RequestParam(required = false) Long organizerId,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable);

    // q: buyer email, attendee name or order id.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/orders")
    ResponseEntity<Page<AdminOrderResponse>> searchOrders(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long eventId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);

    // Every payment attempt; ?status=FAILED lists payment failures. q: reference, buyer email or order id.
    // provider: SIMULATED or HALYK. from/to: inclusive dates.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/payments")
    ResponseEntity<Page<AdminPaymentResponse>> searchPayments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);

    // q: refund reference, buyer email or order id.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/refunds")
    ResponseEntity<Page<AdminRefundResponse>> searchRefunds(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RefundStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);

    // Promo-code activity across events, with redemptions and revenue per campaign.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/campaigns")
    ResponseEntity<Page<PromoCampaignResponse>> searchCampaigns(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);

    // Reports filed by users (POST /api/events/{id}/reports). Newest first; ?status=OPEN is the review queue.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/event-reports")
    ResponseEntity<Page<EventReportResponse>> getEventReports(
            @RequestParam(required = false) EventReport.Status status,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) EventReport.Reason reason,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/event-reports/{reportId}")
    ResponseEntity<EventReportResponse> getEventReport(@PathVariable Long reportId);

    // Closes an OPEN report as DISMISSED or ACTIONED and thanks the reporter. It doesn't change the event:
    // suspend it with POST /api/events/{id}/suspend.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/event-reports/{reportId}/review")
    ResponseEntity<EventReportResponse> reviewEventReport(@AuthenticationPrincipal UserPrincipal currentUser,
                                                          @PathVariable Long reportId,
                                                          @Valid @RequestBody ReviewEventReportRequest request);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/settings")
    ResponseEntity<PlatformSettingsResponse> getSettings();

    // Activation fee and processing fee. Applies to fees charged from now on; past payments keep theirs.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/settings")
    ResponseEntity<PlatformSettingsResponse> updateSettings(@AuthenticationPrincipal UserPrincipal currentUser,
                                                            @Valid @RequestBody UpdatePlatformSettingsRequest request);

    // CSV downloads. from/to: inclusive dates, default the last 30 days, at most 366 days.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/reports/events.csv")
    ResponseEntity<byte[]> eventsReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/reports/payments.csv")
    ResponseEntity<byte[]> paymentsReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}
