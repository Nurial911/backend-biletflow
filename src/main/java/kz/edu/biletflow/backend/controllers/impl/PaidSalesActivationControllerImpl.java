package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.PaidSalesActivationController;
import kz.edu.biletflow.backend.dtos.PaidSalesActivationResponse;
import kz.edu.biletflow.backend.dtos.SuspendEventRequest;
import kz.edu.biletflow.backend.entities.PaidSalesStatus;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.PaidSalesActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaidSalesActivationControllerImpl implements PaidSalesActivationController {
    private final PaidSalesActivationService activationService;

    @Override
    public ResponseEntity<PaidSalesActivationResponse> getActivation(UserPrincipal currentUser, Long eventId) {
        Long requesterId = currentUser != null ? currentUser.getId() : null;
        boolean isPlatformAdmin = currentUser != null && currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(activationService.getActivation(eventId, requesterId, isPlatformAdmin));
    }

    @Override
    public ResponseEntity<PaidSalesActivationResponse> payFee(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(activationService.payFee(currentUser.getId(), eventId));
    }

    @Override
    public ResponseEntity<PaidSalesActivationResponse> acceptTerms(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(activationService.acceptTerms(currentUser.getId(), eventId));
    }

    @Override
    public ResponseEntity<PaidSalesActivationResponse> activate(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(activationService.activate(currentUser.getId(), eventId));
    }

    @Override
    public ResponseEntity<PaidSalesActivationResponse> suspend(UserPrincipal currentUser, Long eventId, SuspendEventRequest request) {
        return ResponseEntity.ok(activationService.suspend(currentUser.getId(), eventId,
                request != null ? request.getReason() : null));
    }

    @Override
    public ResponseEntity<PaidSalesActivationResponse> unsuspend(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(activationService.unsuspend(currentUser.getId(), eventId));
    }

    @Override
    public ResponseEntity<Page<PaidSalesActivationResponse>> getActivations(PaidSalesStatus status, Pageable pageable) {
        return ResponseEntity.ok(activationService.getActivations(status, pageable));
    }
}
