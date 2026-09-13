package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateOrganizerProfileRequest;
import kz.edu.biletflow.backend.dtos.OrganizerProfileResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("api/organizers")
public interface OrganizerProfileController {

    @PreAuthorize("hasRole('ATTENDEE')")
    @PostMapping("/profile")
    ResponseEntity<OrganizerProfileResponse> createOrganizerProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateOrganizerProfileRequest request
    );

    @PreAuthorize("hasAnyRole('ORGANIZER', 'EVENT_ADMIN', 'PLATFORM_ADMIN')")
    @GetMapping("/profile")
    ResponseEntity<OrganizerProfileResponse> getOrganizerProfile(@AuthenticationPrincipal UserPrincipal currentUser);
}
