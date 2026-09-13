package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CampaignRequest;
import kz.edu.biletflow.backend.entities.DiscountType;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.impl.PromoServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PromoServiceImplTest {
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final PromotionalCampaignRepository campaignRepository = mock(PromotionalCampaignRepository.class);
    private final PromoCodeRepository promoCodeRepository = mock(PromoCodeRepository.class);
    private final PromoRedemptionRepository redemptionRepository = mock(PromoRedemptionRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);

    @Test
    void rejectsPercentageCampaignAboveOneHundred() {
        User organizer = new User();
        organizer.setId(1L);
        Event event = new Event();
        event.setOrganizer(organizer);
        when(eventRepository.findById(2L)).thenReturn(Optional.of(event));

        CampaignRequest request = new CampaignRequest();
        request.setDiscountType(DiscountType.PERCENTAGE);
        request.setDiscountValue(BigDecimal.valueOf(101));
        request.setCode("TOO-MUCH");

        PromoService service = new PromoServiceImpl(eventRepository, campaignRepository,
                promoCodeRepository, redemptionRepository, orderRepository);

        assertThrows(BusinessRuleViolationException.class, () -> service.createCampaign(1L, 2L, request));
        verify(campaignRepository, never()).save(any());
    }
}
