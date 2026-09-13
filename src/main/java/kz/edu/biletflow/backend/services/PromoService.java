package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CampaignRequest;
import kz.edu.biletflow.backend.entities.PromoCode;

public interface PromoService {
    PromoCode createCampaign(Long organizerId, Long eventId, CampaignRequest request);
    void redeem(Long userId, Long orderId, String code);
}
