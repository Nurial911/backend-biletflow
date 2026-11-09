package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.OrganizerProfileController;
import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.OrganizerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class OrganizerProfileControllerImpl implements OrganizerProfileController {
    private final OrganizerProfileService organizerProfileService;

    @Override
    public ResponseEntity<OrganizerProfileCreatedResponse> createOrganizerProfile(UserPrincipal currentUser, CreateOrganizerProfileRequest request) {
        OrganizerProfileCreatedResponse createdProfile = organizerProfileService.createProfile(currentUser.getId(), request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .build()
                .toUri();
        return ResponseEntity.created(location).body(createdProfile);
    }

    @Override
    public ResponseEntity<OrganizerProfileResponse> getOrganizerProfile(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(organizerProfileService.getOrganizerProfile(currentUser.getId()));
    }

    @Override
    public ResponseEntity<OrganizerProfileResponse> requestVerification(UserPrincipal currentUser) {
        return ResponseEntity.ok(organizerProfileService.requestVerification(currentUser.getId()));
    }

    @Override
    public ResponseEntity<PayoutAccountResponse> savePayoutAccount(UserPrincipal currentUser, PayoutAccountRequest request) {
        return ResponseEntity.ok(organizerProfileService.savePayoutAccount(currentUser.getId(), request));
    }

    @Override
    public ResponseEntity<PayoutAccountResponse> getPayoutAccount(UserPrincipal currentUser) {
        return ResponseEntity.ok(organizerProfileService.getPayoutAccount(currentUser.getId()));
    }

    @Override
    public ResponseEntity<Page<OrganizerVerificationRequestResponse>> getPendingVerifications(Pageable pageable) {
        return ResponseEntity.ok(organizerProfileService.getPendingVerifications(pageable));
    }

    @Override
    public ResponseEntity<OrganizerProfileResponse> reviewVerification(UserPrincipal currentUser, Long profileId, ReviewOrganizerRequest request) {
        return ResponseEntity.ok(organizerProfileService.reviewVerification(
                currentUser.getId(), profileId, request.getApproved(), request.getNote()));
    }
}
