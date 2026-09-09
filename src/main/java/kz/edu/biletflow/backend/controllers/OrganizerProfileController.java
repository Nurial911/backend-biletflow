package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateOrganizerProfileRequest;
import kz.edu.biletflow.backend.dtos.OrganizerProfileResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("api/organizers")
public interface OrganizerProfileController {

    // Пока мы не подключили Spring Security и токены, передаем userId в URL
    @PostMapping("/profile")
    ResponseEntity<OrganizerProfileResponse> createOrganizerProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateOrganizerProfileRequest request
    );

    @GetMapping("/profile")
    ResponseEntity<OrganizerProfileResponse> getOrganizerProfile(@AuthenticationPrincipal UserPrincipal currentUser);
}
