package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.PromoCampaignController;
import kz.edu.biletflow.backend.dtos.CreatePromoCampaignRequest;
import kz.edu.biletflow.backend.dtos.PromoCampaignResponse;
import kz.edu.biletflow.backend.dtos.PromoPreviewResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.PromoCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PromoCampaignControllerImpl implements PromoCampaignController {
    private final PromoCampaignService promoCampaignService;

    @Override
    public ResponseEntity<PromoCampaignResponse> createCampaign(UserPrincipal currentUser, Long eventId, CreatePromoCampaignRequest request) {
        PromoCampaignResponse created = promoCampaignService.createCampaign(currentUser.getId(), eventId, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<List<PromoCampaignResponse>> getCampaigns(UserPrincipal currentUser, Long eventId) {
        return ResponseEntity.ok(promoCampaignService.getCampaigns(eventId, id(currentUser), isAdmin(currentUser)));
    }

    @Override
    public ResponseEntity<PromoCampaignResponse> getCampaign(UserPrincipal currentUser, Long eventId, Long campaignId) {
        return ResponseEntity.ok(promoCampaignService.getCampaign(eventId, campaignId, id(currentUser), isAdmin(currentUser)));
    }

    @Override
    public ResponseEntity<PromoCampaignResponse> disableCampaign(UserPrincipal currentUser, Long eventId, Long campaignId) {
        return ResponseEntity.ok(promoCampaignService.setEnabled(currentUser.getId(), eventId, campaignId, false));
    }

    @Override
    public ResponseEntity<PromoCampaignResponse> enableCampaign(UserPrincipal currentUser, Long eventId, Long campaignId) {
        return ResponseEntity.ok(promoCampaignService.setEnabled(currentUser.getId(), eventId, campaignId, true));
    }

    @Override
    public ResponseEntity<byte[]> getCampaignQr(UserPrincipal currentUser, Long eventId, Long campaignId) {
        byte[] png = promoCampaignService.getCampaignQr(eventId, campaignId, id(currentUser), isAdmin(currentUser));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .body(png);
    }

    @Override
    public ResponseEntity<PromoPreviewResponse> previewPromo(UserPrincipal currentUser, Long eventId, String code, String token) {
        return ResponseEntity.ok(promoCampaignService.preview(eventId, code, token, id(currentUser), isAdmin(currentUser)));
    }

    private static Long id(UserPrincipal currentUser) {
        return currentUser != null ? currentUser.getId() : null;
    }

    private static boolean isAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.hasRole("PLATFORM_ADMIN");
    }
}
