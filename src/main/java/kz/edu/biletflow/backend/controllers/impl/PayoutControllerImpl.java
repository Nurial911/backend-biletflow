package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.PayoutController;
import kz.edu.biletflow.backend.dtos.HoldPayoutRequest;
import kz.edu.biletflow.backend.dtos.PayoutResponse;
import kz.edu.biletflow.backend.entities.Payout;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.AdminReportService;
import kz.edu.biletflow.backend.services.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class PayoutControllerImpl implements PayoutController {
    private static final MediaType CSV = MediaType.parseMediaType("text/csv; charset=utf-8");

    private final PayoutService payoutService;
    private final AdminReportService adminReportService;

    @Override
    public ResponseEntity<Page<PayoutResponse>> getMyPayouts(UserPrincipal currentUser, Payout.Status status,
                                                             Pageable pageable) {
        return ResponseEntity.ok(payoutService.getOrganizerPayouts(currentUser.getId(), status, pageable));
    }

    @Override
    public ResponseEntity<PayoutResponse> getEventPayout(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(payoutService.getEventPayout(eventId, currentUser.getId(),
                currentUser.hasRole("PLATFORM_ADMIN")));
    }

    @Override
    public ResponseEntity<Page<PayoutResponse>> searchPayouts(String q, Payout.Status status, Long organizerId,
                                                              Long eventId, Pageable pageable) {
        return ResponseEntity.ok(payoutService.searchPayouts(q, status, organizerId, eventId, pageable));
    }

    @Override
    public ResponseEntity<PayoutResponse> getPayout(Long payoutId) {
        return ResponseEntity.ok(payoutService.getPayout(payoutId));
    }

    @Override
    public ResponseEntity<PayoutResponse> holdPayout(UserPrincipal currentUser, Long payoutId, HoldPayoutRequest request) {
        return ResponseEntity.ok(payoutService.holdPayout(currentUser.getId(), payoutId, request.getReason()));
    }

    @Override
    public ResponseEntity<PayoutResponse> releasePayout(UserPrincipal currentUser, Long payoutId) {
        return ResponseEntity.ok(payoutService.releasePayout(currentUser.getId(), payoutId));
    }

    @Override
    public ResponseEntity<PayoutResponse> payNow(UserPrincipal currentUser, Long payoutId) {
        return ResponseEntity.ok(payoutService.payNow(currentUser.getId(), payoutId));
    }

    @Override
    public ResponseEntity<byte[]> payoutsReport(LocalDate from, LocalDate to) {
        AdminReportService.CsvReport report = adminReportService.payoutsReport(from, to);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(report.fileName()).build().toString())
                .body(report.content());
    }
}
