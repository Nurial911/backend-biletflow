package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CampaignRequest;
import kz.edu.biletflow.backend.dtos.RedeemPromoRequest;
import kz.edu.biletflow.backend.entities.PromoCode;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.PromoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PromoController {
    private final PromoService promoService;

    @PostMapping("/events/{eventId}/campaigns")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<PromoCode> create(@AuthenticationPrincipal UserPrincipal user,
                                             @PathVariable Long eventId,
                                             @Valid @RequestBody CampaignRequest request) {
        return ResponseEntity.ok(promoService.createCampaign(user.getId(), eventId, request));
    }

    @PostMapping("/orders/{orderId}/promo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> redeem(@AuthenticationPrincipal UserPrincipal user,
                                       @PathVariable Long orderId,
                                       @Valid @RequestBody RedeemPromoRequest request) {
        promoService.redeem(user.getId(), orderId, request.getCode());
        return ResponseEntity.noContent().build();
    }
}
