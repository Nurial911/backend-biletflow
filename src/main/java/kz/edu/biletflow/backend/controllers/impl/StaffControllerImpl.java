package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.StaffController;
import kz.edu.biletflow.backend.dtos.AssignStaffRequest;
import kz.edu.biletflow.backend.dtos.StaffResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class StaffControllerImpl implements StaffController {
    private final StaffService staffService;

    @Override
    public ResponseEntity<StaffResponse> assignStaff(UserPrincipal currentUser, Long eventId, AssignStaffRequest request) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        StaffResponse created = staffService.assignStaff(eventId, currentUser.getId(), isPlatformAdmin, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/events/{eventId}/staff")
                .buildAndExpand(eventId)
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<List<StaffResponse>> getStaff(UserPrincipal currentUser, Long eventId) {
        Long requesterId = currentUser != null ? currentUser.getId() : null;
        boolean isPlatformAdmin = currentUser != null && currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(staffService.getStaff(eventId, requesterId, isPlatformAdmin));
    }

    @Override
    public ResponseEntity<Void> removeStaff(UserPrincipal currentUser, Long eventId, Long userId) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        staffService.removeStaff(eventId, userId, currentUser.getId(), isPlatformAdmin);
        return ResponseEntity.noContent().build();
    }
}
