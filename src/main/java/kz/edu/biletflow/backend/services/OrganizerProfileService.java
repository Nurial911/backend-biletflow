package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface OrganizerProfileService {
    OrganizerProfileCreatedResponse createProfile(Long userId,
                                                           CreateOrganizerProfileRequest request);

    OrganizerProfileResponse getOrganizerProfile(Long userId);

    // Simulated identity check: the organizer asks, a platform admin decides. No documents are collected.
    OrganizerProfileResponse requestVerification(Long userId);

    Page<OrganizerVerificationRequestResponse> getPendingVerifications(Pageable pageable);

    OrganizerProfileResponse reviewVerification(Long adminId, Long profileId, boolean approved, @Nullable String note);

    PayoutAccountResponse savePayoutAccount(Long userId, PayoutAccountRequest request);

    PayoutAccountResponse getPayoutAccount(Long userId);
}
