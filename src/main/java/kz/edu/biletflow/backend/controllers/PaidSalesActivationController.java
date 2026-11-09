package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.PaidSalesActivationResponse;
import kz.edu.biletflow.backend.dtos.SuspendEventRequest;
import kz.edu.biletflow.backend.entities.PaidSalesStatus;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// Simulated paid-sales activation of one event: checklist, fee, terms, activation, moderation.
public interface PaidSalesActivationController {
    // Public at the filter level (GET /api/events/**); the service allows only owner/admin.
    @GetMapping("/api/events/{eventId}/activation")
    ResponseEntity<PaidSalesActivationResponse> getActivation(@AuthenticationPrincipal UserPrincipal currentUser,
                                                              @PathVariable Long eventId);

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/api/events/{eventId}/activation/pay-fee")
    ResponseEntity<PaidSalesActivationResponse> payFee(@AuthenticationPrincipal UserPrincipal currentUser,
                                                       @PathVariable Long eventId);

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/api/events/{eventId}/activation/accept-terms")
    ResponseEntity<PaidSalesActivationResponse> acceptTerms(@AuthenticationPrincipal UserPrincipal currentUser,
                                                            @PathVariable Long eventId);

    // 400 listing what is still missing unless the whole checklist is done.
    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/api/events/{eventId}/activation/activate")
    ResponseEntity<PaidSalesActivationResponse> activate(@AuthenticationPrincipal UserPrincipal currentUser,
                                                         @PathVariable Long eventId);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/api/events/{eventId}/activation/suspend")
    ResponseEntity<PaidSalesActivationResponse> suspend(@AuthenticationPrincipal UserPrincipal currentUser,
                                                        @PathVariable Long eventId,
                                                        @Valid @RequestBody(required = false) SuspendEventRequest request);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/api/events/{eventId}/activation/unsuspend")
    ResponseEntity<PaidSalesActivationResponse> unsuspend(@AuthenticationPrincipal UserPrincipal currentUser,
                                                          @PathVariable Long eventId);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/api/admin/paid-sales-activations")
    ResponseEntity<Page<PaidSalesActivationResponse>> getActivations(@RequestParam(required = false) PaidSalesStatus status,
                                                                     Pageable pageable);
}
