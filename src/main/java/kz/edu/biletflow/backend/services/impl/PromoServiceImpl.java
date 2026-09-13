package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.CampaignRequest;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.PromoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromoServiceImpl implements PromoService {
    private final EventRepository eventRepository;
    private final PromotionalCampaignRepository campaignRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository redemptionRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public PromoCode createCampaign(Long organizerId, Long eventId, CampaignRequest request) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new ResourceNotFoundException("Event not found"));
        if (!event.getOrganizer().getId().equals(organizerId)) throw new ForbiddenOperationException("Not event owner");
        if (request.getDiscountType() == DiscountType.PERCENTAGE && request.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleViolationException("Percentage discount cannot exceed 100");
        }
        PromotionalCampaign campaign = new PromotionalCampaign();
        campaign.setEvent(event);
        campaign.setDiscountType(request.getDiscountType());
        campaign.setDiscountValue(request.getDiscountValue());
        campaign.setValidityStart(request.getValidityStart());
        campaign.setValidityEnd(request.getValidityEnd());
        campaign.setMaxRedemptions(request.getMaxRedemptions());
        campaign.setApplicableTicketTypes(request.getApplicableTicketTypes());
        PromoCode promoCode = new PromoCode();
        promoCode.setCampaign(campaignRepository.save(campaign));
        promoCode.setCodeString(request.getCode());
        promoCode.setCampaignQrCodeToken(UUID.randomUUID().toString());
        return promoCodeRepository.save(promoCode);
    }

    @Override
    @Transactional
    public void redeem(Long userId, Long orderId, String code) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getAttendee().getUser().getId().equals(userId)) throw new ForbiddenOperationException("Not order owner");
        if (order.getStatus() != OrderStatus.PENDING || redemptionRepository.existsByOrderId(orderId)) throw new BusinessRuleViolationException("Promo code cannot be applied to this order");
        PromoCode promo = promoCodeRepository.findByCodeStringIgnoreCase(code).orElseThrow(() -> new BusinessRuleViolationException("Invalid promo code"));
        PromotionalCampaign campaign = promo.getCampaign();
        LocalDateTime now = LocalDateTime.now();
        if (!campaign.getEvent().getId().equals(order.getEvent().getId())
                || campaign.getValidityStart() != null && now.isBefore(campaign.getValidityStart())
                || campaign.getValidityEnd() != null && now.isAfter(campaign.getValidityEnd())) {
            throw new BusinessRuleViolationException("Promo code is not valid for this order");
        }
        if (campaign.getMaxRedemptions() != null && redemptionRepository.countByPromoCodeId(promo.getId()) >= campaign.getMaxRedemptions()) {
            throw new BusinessRuleViolationException("Promo code redemption limit reached");
        }
        BigDecimal discount = campaign.getDiscountType() == DiscountType.PERCENTAGE
                ? order.getTotalAmount().multiply(campaign.getDiscountValue()).divide(BigDecimal.valueOf(100))
                : campaign.getDiscountValue();
        order.setTotalAmount(order.getTotalAmount().subtract(discount).max(BigDecimal.ZERO));
        PromoRedemption redemption = new PromoRedemption();
        redemption.setPromoCode(promo);
        redemption.setOrder(order);
        redemptionRepository.save(redemption);
        orderRepository.save(order);
    }
}
