package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.FreeRegistrationRequest;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/registrations")
@RequiredArgsConstructor
public class RegistrationController {
    private final RegistrationService registrationService;

    @PostMapping("/free")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> registerFree(@AuthenticationPrincipal UserPrincipal user,
                                                       @PathVariable Long eventId,
                                                       @Valid @RequestBody FreeRegistrationRequest request) {
        return ResponseEntity.ok(registrationService.registerFree(user.getId(), eventId, request));
    }
}
