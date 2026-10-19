package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CreateOrganizerProfileRequest;
import kz.edu.biletflow.backend.dtos.OrganizerProfileCreatedResponse;
import kz.edu.biletflow.backend.dtos.OrganizerProfileResponse;


public interface OrganizerProfileService {
    OrganizerProfileCreatedResponse createProfile(Long userId,
                                                           CreateOrganizerProfileRequest request);

    OrganizerProfileResponse getOrganizerProfile(Long userId);
}
