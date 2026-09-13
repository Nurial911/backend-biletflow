package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.SeatHoldResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.SeatHoldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatHoldController {
    private final SeatHoldService seatHoldService;

    @PostMapping("/{seatId}/hold")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SeatHoldResponse> hold(@AuthenticationPrincipal UserPrincipal user,
                                                  @PathVariable Long seatId) {
        return ResponseEntity.ok(seatHoldService.hold(user.getId(), seatId));
    }

    @DeleteMapping("/holds/{holdId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> release(@AuthenticationPrincipal UserPrincipal user,
                                         @PathVariable Long holdId) {
        seatHoldService.release(user.getId(), holdId);
        return ResponseEntity.noContent().build();
    }
}
