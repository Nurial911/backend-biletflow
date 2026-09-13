package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support/cases")
@RequiredArgsConstructor
public class SupportController {
    private final SupportService supportService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupportCaseResponse> create(@AuthenticationPrincipal UserPrincipal user,
                                                       @Valid @RequestBody CreateSupportCaseRequest request) {
        return ResponseEntity.ok(supportService.create(user.getId(), request));
    }

    @PostMapping("/{caseId}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupportCaseResponse> message(@AuthenticationPrincipal UserPrincipal user,
                                                        @PathVariable Long caseId,
                                                        @Valid @RequestBody SupportMessageRequest request) {
        return ResponseEntity.ok(supportService.addMessage(user.getId(), caseId, request));
    }

    @PatchMapping("/{caseId}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SupportCaseResponse> status(@AuthenticationPrincipal UserPrincipal user,
                                                       @PathVariable Long caseId,
                                                       @Valid @RequestBody SupportStatusRequest request) {
        return ResponseEntity.ok(supportService.updateStatus(user.getId(), caseId, request));
    }
}
