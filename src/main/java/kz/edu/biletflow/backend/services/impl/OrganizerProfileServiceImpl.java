package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.CreateOrganizerProfileRequest;
import kz.edu.biletflow.backend.dtos.OrganizerProfileCreatedResponse;
import kz.edu.biletflow.backend.dtos.OrganizerProfileResponse;
import kz.edu.biletflow.backend.entities.OrganizerProfile;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.DuplicateResourceException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.OrganizerProfileMapper;
import kz.edu.biletflow.backend.repositories.OrganizerProfileRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.security.JwtService;
import kz.edu.biletflow.backend.services.OrganizerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizerProfileServiceImpl implements OrganizerProfileService {
    private final OrganizerProfileRepository organizerProfileRepository;
    private final UserRepository userRepository;
    private final OrganizerProfileMapper organizerProfileMapper;
    private final JwtService jwtService;

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
        organizerProfile.setVerificationStatus("UNVERIFIED");
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
}
