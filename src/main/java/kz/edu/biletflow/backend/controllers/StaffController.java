package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.AssignStaffRequest;
import kz.edu.biletflow.backend.dtos.StaffResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Check-in staff (EVENT_ADMIN users) of an event, managed by its organizer.
@RequestMapping("/api/events/{eventId}/staff")
public interface StaffController {
    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @PostMapping
    ResponseEntity<StaffResponse> assignStaff(@AuthenticationPrincipal UserPrincipal currentUser,
                                              @PathVariable Long eventId,
                                              @Valid @RequestBody AssignStaffRequest request);

    // Public at the filter level (GET /api/events/**); the service allows only owner/admin.
    @GetMapping
    ResponseEntity<List<StaffResponse>> getStaff(@AuthenticationPrincipal UserPrincipal currentUser,
                                                 @PathVariable Long eventId);

    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @DeleteMapping("/{userId}")
    ResponseEntity<Void> removeStaff(@AuthenticationPrincipal UserPrincipal currentUser,
                                     @PathVariable Long eventId,
                                     @PathVariable Long userId);
}
