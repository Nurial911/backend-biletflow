package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.TicketResponse;
import kz.edu.biletflow.backend.dtos.TicketVerificationRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verification/tickets")
@RequiredArgsConstructor
public class VerificationController {
    private final VerificationService verificationService;

    @PostMapping("/{ticketIdentifier}/validate")
    @PreAuthorize("hasAnyRole('EVENT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<TicketResponse> validate(@PathVariable String ticketIdentifier,
                                                    @Valid @RequestBody TicketVerificationRequest request) {
        return ResponseEntity.ok(verificationService.validate(ticketIdentifier, request.getQrPayload()));
    }

    @PostMapping("/{ticketIdentifier}/check-in")
    @PreAuthorize("hasAnyRole('EVENT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<TicketResponse> checkIn(@AuthenticationPrincipal UserPrincipal user,
                                                   @PathVariable String ticketIdentifier,
                                                   @Valid @RequestBody TicketVerificationRequest request) {
        return ResponseEntity.ok(verificationService.checkIn(user.getId(), ticketIdentifier, request.getQrPayload()));
    }

    @PostMapping("/{ticketIdentifier}/undo-check-in")
    @PreAuthorize("hasAnyRole('EVENT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<TicketResponse> undoCheckIn(@AuthenticationPrincipal UserPrincipal user,
                                                       @PathVariable String ticketIdentifier) {
        return ResponseEntity.ok(verificationService.undoCheckIn(user.getId(), ticketIdentifier));
    }
}
