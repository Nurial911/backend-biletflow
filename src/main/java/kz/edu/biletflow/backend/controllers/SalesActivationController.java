package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.ActivationRequest;
import kz.edu.biletflow.backend.dtos.ActivationResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.SalesActivationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/sales-activation")
@RequiredArgsConstructor
public class SalesActivationController {
    private final SalesActivationService salesActivationService;

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ActivationResponse> activate(@AuthenticationPrincipal UserPrincipal user,
                                                        @PathVariable Long eventId,
                                                        @Valid @RequestBody ActivationRequest request) {
        return ResponseEntity.ok(salesActivationService.activate(user.getId(), eventId, request));
    }
}
