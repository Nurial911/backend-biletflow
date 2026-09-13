package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.AnalyticsResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizer/events")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping("/{eventId}/analytics")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AnalyticsResponse> analytics(@AuthenticationPrincipal UserPrincipal user,
                                                        @PathVariable Long eventId) {
        return ResponseEntity.ok(analyticsService.eventAnalytics(user.getId(), eventId));
    }
}
