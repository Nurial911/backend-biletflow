package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateOrganizerProfileRequest;
import kz.edu.biletflow.backend.dtos.OrganizerProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("api/organizers")
public interface OrganizerProfileController {

    // Пока мы не подключили Spring Security и токены, передаем userId в URL
    @PostMapping("/{userId}/profile")
    ResponseEntity<OrganizerProfileResponse> createOrganizerProfile(
            @PathVariable Long userId,
            @Valid @RequestBody CreateOrganizerProfileRequest request
    );

    @GetMapping("/{userId}/profile")
    ResponseEntity<OrganizerProfileResponse> getOrganizerProfile(@PathVariable Long userId);
}
