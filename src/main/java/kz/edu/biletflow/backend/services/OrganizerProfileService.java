package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CreateOrganizerProfileRequest;
import kz.edu.biletflow.backend.dtos.OrganizerProfileResponse;


public interface OrganizerProfileService {
    OrganizerProfileResponse createProfile(Long userId,
                                           CreateOrganizerProfileRequest request);

    OrganizerProfileResponse getOrganizerProfile(Long userId);
}
