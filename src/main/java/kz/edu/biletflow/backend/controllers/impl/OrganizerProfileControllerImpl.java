package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.OrganizerProfileController;
import kz.edu.biletflow.backend.dtos.CreateOrganizerProfileRequest;
import kz.edu.biletflow.backend.dtos.OrganizerProfileResponse;
import kz.edu.biletflow.backend.services.OrganizerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class OrganizerProfileControllerImpl implements OrganizerProfileController {
    private final OrganizerProfileService organizerProfileService;

    @Override
    public ResponseEntity<OrganizerProfileResponse> createOrganizerProfile(Long userId, CreateOrganizerProfileRequest request) {
        OrganizerProfileResponse createdProfile = organizerProfileService.createProfile(userId, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .build()
                .toUri();
        return ResponseEntity.created(location).body(createdProfile);
    }

    @Override
    public ResponseEntity<OrganizerProfileResponse> getOrganizerProfile(Long userId) {
        return ResponseEntity.ok(organizerProfileService.getOrganizerProfile(userId));
    }
}
