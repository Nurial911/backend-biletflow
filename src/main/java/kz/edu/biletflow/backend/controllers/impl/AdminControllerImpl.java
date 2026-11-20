package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.AdminController;
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
import kz.edu.biletflow.backend.services.AdminReportService;
import kz.edu.biletflow.backend.services.AdminService;
import kz.edu.biletflow.backend.services.EventReportService;
import kz.edu.biletflow.backend.services.PlatformSettingsService;
import kz.edu.biletflow.backend.services.PromoCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class AdminControllerImpl implements AdminController {
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final AdminService adminService;
    private final EventReportService eventReportService;
    private final PlatformSettingsService platformSettingsService;
    private final PromoCampaignService promoCampaignService;
    private final AdminReportService adminReportService;

    @Override
    public ResponseEntity<AdminOverviewResponse> getOverview() {
        return ResponseEntity.ok(adminService.getOverview());
    }

    @Override
    public ResponseEntity<Page<EventResponse>> searchEvents(String q, EventStatus status, Boolean suspended,
                                                            Long organizerId, Pageable pageable) {
        return ResponseEntity.ok(adminService.searchEvents(q, status, suspended, organizerId, pageable));
    }

    @Override
    public ResponseEntity<Page<AdminOrderResponse>> searchOrders(String q, OrderStatus status, Long eventId,
                                                                 Pageable pageable) {
        return ResponseEntity.ok(adminService.searchOrders(q, status, eventId, pageable));
    }

    @Override
    public ResponseEntity<Page<AdminPaymentResponse>> searchPayments(String q, PaymentStatus status, String provider,
                                                                     LocalDate from, LocalDate to, Pageable pageable) {
        return ResponseEntity.ok(adminService.searchPayments(q, status, provider, from, to, pageable));
    }

    @Override
    public ResponseEntity<Page<AdminRefundResponse>> searchRefunds(String q, RefundStatus status, Pageable pageable) {
        return ResponseEntity.ok(adminService.searchRefunds(q, status, pageable));
    }

    @Override
    public ResponseEntity<Page<PromoCampaignResponse>> searchCampaigns(Long eventId, Boolean enabled, Pageable pageable) {
        return ResponseEntity.ok(promoCampaignService.searchCampaigns(eventId, enabled, pageable));
    }

    @Override
    public ResponseEntity<Page<EventReportResponse>> getEventReports(EventReport.Status status, Long eventId,
                                                                     EventReport.Reason reason, Pageable pageable) {
        return ResponseEntity.ok(eventReportService.getReports(status, eventId, reason, pageable));
    }

    @Override
    public ResponseEntity<EventReportResponse> getEventReport(Long reportId) {
        return ResponseEntity.ok(eventReportService.getReport(reportId));
    }

    @Override
    public ResponseEntity<EventReportResponse> reviewEventReport(UserPrincipal currentUser, Long reportId,
                                                                 ReviewEventReportRequest request) {
        return ResponseEntity.ok(eventReportService.reviewReport(currentUser.getId(), reportId, request));
    }

    @Override
    public ResponseEntity<PlatformSettingsResponse> getSettings() {
        return ResponseEntity.ok(platformSettingsService.getSettings());
    }

    @Override
    public ResponseEntity<PlatformSettingsResponse> updateSettings(UserPrincipal currentUser,
                                                                   UpdatePlatformSettingsRequest request) {
        return ResponseEntity.ok(platformSettingsService.updateSettings(currentUser.getId(), request));
    }

    @Override
    public ResponseEntity<byte[]> eventsReport(LocalDate from, LocalDate to) {
        return csv(adminReportService.eventsReport(from, to));
    }

    @Override
    public ResponseEntity<byte[]> paymentsReport(LocalDate from, LocalDate to) {
        return csv(adminReportService.paymentsReport(from, to));
    }

    private static ResponseEntity<byte[]> csv(AdminReportService.CsvReport report) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.fileName()).build().toString())
                .body(report.content());
    }
}
