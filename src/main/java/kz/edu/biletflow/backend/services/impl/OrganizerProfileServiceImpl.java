package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.DuplicateResourceException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.OrganizerProfileMapper;
import kz.edu.biletflow.backend.repositories.OrganizerProfileRepository;
import kz.edu.biletflow.backend.repositories.PayoutAccountRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.security.JwtService;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.NotificationService;
import kz.edu.biletflow.backend.services.OrganizerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizerProfileServiceImpl implements OrganizerProfileService {
    private final OrganizerProfileRepository organizerProfileRepository;
    private final UserRepository userRepository;
    private final OrganizerProfileMapper organizerProfileMapper;
    private final JwtService jwtService;
    private final PayoutAccountRepository payoutAccountRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public OrganizerProfileCreatedResponse createProfile(Long userId, CreateOrganizerProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        if (organizerProfileRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("Profile already exists for this user");
        }

        if (user.getRole() != User.Role.ATTENDEE) {
            throw new ForbiddenOperationException("Only attendee accounts can become organizers");
        }

        OrganizerProfile organizerProfile = new OrganizerProfile();
        organizerProfile.setUser(user);
        organizerProfile.setContactInformation(request.getContactInformation());
        organizerProfile.setVerificationStatus(OrganizerVerificationStatus.UNVERIFIED);
        user.setRole(User.Role.ORGANIZER);
        userRepository.save(user);

        OrganizerProfile savedProfile = organizerProfileRepository.save(organizerProfile);
        String token = jwtService.generateToken(user);
        return new OrganizerProfileCreatedResponse(organizerProfileMapper.toDto(savedProfile), token);
    }

    @Override
    public OrganizerProfileResponse getOrganizerProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        if (!organizerProfileRepository.existsByUserId(userId)) {
            throw new ResourceNotFoundException("Profile not found for user with id " + userId);
        }

        OrganizerProfile organizerProfile = organizerProfileRepository.findByUserId(userId);
        return organizerProfileMapper.toDto(organizerProfile);
    }

    @Override
    @Transactional
    public OrganizerProfileResponse requestVerification(Long userId) {
        OrganizerProfile profile = getOwnProfileForUpdate(userId);
        OrganizerVerificationStatus status = profile.getVerificationStatus();
        if (status != OrganizerVerificationStatus.UNVERIFIED && status != OrganizerVerificationStatus.REJECTED) {
            throw new BusinessRuleViolationException("Verification is already " + status);
        }
        profile.setVerificationStatus(OrganizerVerificationStatus.PENDING);
        return organizerProfileMapper.toDto(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizerVerificationRequestResponse> getPendingVerifications(Pageable pageable) {
        return organizerProfileRepository.findAllByVerificationStatus(OrganizerVerificationStatus.PENDING, pageable)
                .map(organizerProfileMapper::toVerificationRequest);
    }

    @Override
    @Transactional
    public OrganizerProfileResponse reviewVerification(Long adminId, Long profileId, boolean approved, @Nullable String note) {
        OrganizerProfile profile = organizerProfileRepository.findByIdForUpdate(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("Organizer profile not found with id: " + profileId));
        if (profile.getVerificationStatus() != OrganizerVerificationStatus.PENDING) {
            throw new BusinessRuleViolationException("This organizer has no verification request waiting for review");
        }
        profile.setVerificationStatus(approved ? OrganizerVerificationStatus.VERIFIED : OrganizerVerificationStatus.REJECTED);

        String outcome = approved ? "approved" : "rejected";
        String reason = note != null && !note.isBlank() ? " Note from the reviewer: " + note.trim() : "";
        auditService.record(adminId,
                approved ? AuditAction.ORGANIZER_VERIFICATION_APPROVED : AuditAction.ORGANIZER_VERIFICATION_REJECTED,
                profileId, null, "Simulated identity verification " + outcome + "." + reason);
        notificationService.notifyUser(profile.getUser().getId(), NotificationType.ORGANIZER_VERIFICATION,
                "Your organizer verification was " + outcome,
                "Your organizer identity verification was " + outcome + "." + reason, null);
        return organizerProfileMapper.toDto(profile);
    }

    @Override
    @Transactional
    public PayoutAccountResponse savePayoutAccount(Long userId, PayoutAccountRequest request) {
        OrganizerProfile profile = getOwnProfileForUpdate(userId);
        PayoutAccount account = profile.getPayoutAccountId() != null
                ? payoutAccountRepository.findById(profile.getPayoutAccountId()).orElseGet(PayoutAccount::new)
                : new PayoutAccount();
        account.setBankName(request.getBankName().trim());
        account.setAccountHolder(request.getAccountHolder().trim());
        account.setIbanLast4(request.getIban().substring(request.getIban().length() - 4));
        PayoutAccount saved = payoutAccountRepository.save(account);
        profile.setPayoutAccountId(saved.getId());
        return organizerProfileMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PayoutAccountResponse getPayoutAccount(Long userId) {
        OrganizerProfile profile = organizerProfileRepository.findByUserId(userId);
        if (profile == null || profile.getPayoutAccountId() == null) {
            throw new ResourceNotFoundException("No payout account registered yet");
        }
        return payoutAccountRepository.findById(profile.getPayoutAccountId())
                .map(organizerProfileMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("No payout account registered yet"));
    }

    private OrganizerProfile getOwnProfileForUpdate(Long userId) {
        return organizerProfileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found for user with id " + userId));
    }
}
