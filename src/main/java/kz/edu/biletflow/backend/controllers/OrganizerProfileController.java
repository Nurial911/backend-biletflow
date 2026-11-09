package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("api/organizers")
public interface OrganizerProfileController {

    // Пока мы не подключили Spring Security и токены, передаем userId в URL
    @PostMapping("/profile")
    ResponseEntity<OrganizerProfileCreatedResponse> createOrganizerProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateOrganizerProfileRequest request
    );

    @GetMapping("/profile")
    ResponseEntity<OrganizerProfileResponse> getOrganizerProfile(@AuthenticationPrincipal UserPrincipal currentUser);

    // Simulated identity check: moves the profile to PENDING for a platform admin to decide.
    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/profile/verification")
    ResponseEntity<OrganizerProfileResponse> requestVerification(@AuthenticationPrincipal UserPrincipal currentUser);

    // Simulated payout destination; only the last 4 IBAN characters are stored.
    @PreAuthorize("hasRole('ORGANIZER')")
    @PutMapping("/profile/payout-account")
    ResponseEntity<PayoutAccountResponse> savePayoutAccount(@AuthenticationPrincipal UserPrincipal currentUser,
                                                            @Valid @RequestBody PayoutAccountRequest request);

    @PreAuthorize("hasRole('ORGANIZER')")
    @GetMapping("/profile/payout-account")
    ResponseEntity<PayoutAccountResponse> getPayoutAccount(@AuthenticationPrincipal UserPrincipal currentUser);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/verification-requests")
    ResponseEntity<Page<OrganizerVerificationRequestResponse>> getPendingVerifications(Pageable pageable);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/{profileId}/verification")
    ResponseEntity<OrganizerProfileResponse> reviewVerification(@AuthenticationPrincipal UserPrincipal currentUser,
                                                                @PathVariable Long profileId,
                                                                @Valid @RequestBody ReviewOrganizerRequest request);
}
