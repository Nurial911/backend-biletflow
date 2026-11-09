package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreatePromoCampaignRequest;
import kz.edu.biletflow.backend.dtos.PromoCampaignResponse;
import kz.edu.biletflow.backend.dtos.PromoPreviewResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// All GETs are public at the filter level (GET /api/events/**): campaign reads allow only owner/admin.
@RequestMapping("/api/events/{eventId}")
public interface PromoCampaignController {
    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/campaigns")
    ResponseEntity<PromoCampaignResponse> createCampaign(@AuthenticationPrincipal UserPrincipal currentUser,
                                                         @PathVariable Long eventId,
                                                         @Valid @RequestBody CreatePromoCampaignRequest request);

    @GetMapping("/campaigns")
    ResponseEntity<List<PromoCampaignResponse>> getCampaigns(@AuthenticationPrincipal UserPrincipal currentUser,
                                                             @PathVariable Long eventId);

    @GetMapping("/campaigns/{campaignId}")
    ResponseEntity<PromoCampaignResponse> getCampaign(@AuthenticationPrincipal UserPrincipal currentUser,
                                                      @PathVariable Long eventId,
                                                      @PathVariable Long campaignId);

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/campaigns/{campaignId}/disable")
    ResponseEntity<PromoCampaignResponse> disableCampaign(@AuthenticationPrincipal UserPrincipal currentUser,
                                                          @PathVariable Long eventId,
                                                          @PathVariable Long campaignId);

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping("/campaigns/{campaignId}/enable")
    ResponseEntity<PromoCampaignResponse> enableCampaign(@AuthenticationPrincipal UserPrincipal currentUser,
                                                         @PathVariable Long eventId,
                                                         @PathVariable Long campaignId);

    // image/png. No "produces" here on purpose: errors must still be rendered as JSON.
    @GetMapping("/campaigns/{campaignId}/qr")
    ResponseEntity<byte[]> getCampaignQr(@AuthenticationPrincipal UserPrincipal currentUser,
                                         @PathVariable Long eventId,
                                         @PathVariable Long campaignId);

    // For the event page: pass ?code= (typed) or ?token= (from a Campaign QR link). 400 with a clear message if unusable.
    @GetMapping("/promo")
    ResponseEntity<PromoPreviewResponse> previewPromo(@AuthenticationPrincipal UserPrincipal currentUser,
                                                      @PathVariable Long eventId,
                                                      @RequestParam(required = false) String code,
                                                      @RequestParam(required = false) String token);
}
