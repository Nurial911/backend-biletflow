package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.CreatePromoCampaignRequest;
import kz.edu.biletflow.backend.dtos.PromoCampaignResponse;
import kz.edu.biletflow.backend.dtos.PromoPreviewResponse;
import kz.edu.biletflow.backend.entities.OrderItem;
import kz.edu.biletflow.backend.entities.PromoCampaign;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface PromoCampaignService {
    PromoCampaignResponse createCampaign(Long organizerId, Long eventId, CreatePromoCampaignRequest request);

    List<PromoCampaignResponse> getCampaigns(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin);

    PromoCampaignResponse getCampaign(Long eventId, Long campaignId, @Nullable Long requesterId, boolean isPlatformAdmin);

    PromoCampaignResponse setEnabled(Long organizerId, Long eventId, Long campaignId, boolean enabled);

    // PNG of the Campaign QR code.
    byte[] getCampaignQr(Long eventId, Long campaignId, @Nullable Long requesterId, boolean isPlatformAdmin);

    // Validates a typed code or the token from a Campaign QR link. Throws with a clear message if unusable.
    PromoPreviewResponse preview(Long eventId, @Nullable String code, @Nullable String token,
                                 @Nullable Long requesterId, boolean isPlatformAdmin);

    // For checkout. The first holds a row lock until the order's transaction ends.
    PromoCampaign getRedeemableForUpdate(Long eventId, String code, LocalDateTime now);

    PromoCampaign getRedeemable(Long eventId, String code, LocalDateTime now);

    BigDecimal calculateDiscount(PromoCampaign campaign, List<OrderItem> items);
}
