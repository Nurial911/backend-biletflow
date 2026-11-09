package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.CreatePromoCampaignRequest;
import kz.edu.biletflow.backend.dtos.PromoCampaignResponse;
import kz.edu.biletflow.backend.dtos.PromoPreviewResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.EventService;
import kz.edu.biletflow.backend.services.PromoCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromoCampaignServiceImpl implements PromoCampaignService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no look-alikes (0/O, 1/I)
    private static final int CODE_LENGTH = 8;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PromoCampaignRepository campaignRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EventService eventService;
    private final AuditService auditService;
    private final QrCodeGenerator qrCodeGenerator;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public PromoCampaignResponse createCampaign(Long organizerId, Long eventId, CreatePromoCampaignRequest request) {
        Event event = getOwnedEvent(organizerId, eventId);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessRuleViolationException("A cancelled event cannot have new campaigns");
        }
        if (event.isSuspended()) {
            throw new BusinessRuleViolationException("This event is suspended by a platform administrator");
        }
        if (request.getDiscountType() == PromoDiscountType.PERCENT && request.getDiscountValue().compareTo(HUNDRED) > 0) {
            throw new BusinessRuleViolationException("A percentage discount cannot be more than 100");
        }
        if (request.getValidFrom() != null && request.getValidUntil() != null
                && !request.getValidFrom().isBefore(request.getValidUntil())) {
            throw new BusinessRuleViolationException("The campaign must start before it ends");
        }

        PromoCampaign campaign = new PromoCampaign();
        campaign.setEvent(event);
        campaign.setName(request.getName().trim());
        campaign.setCode(generateUniqueCode());
        campaign.setToken(generateToken());
        campaign.setDiscountType(request.getDiscountType());
        campaign.setDiscountValue(request.getDiscountValue());
        campaign.setValidFrom(request.getValidFrom());
        campaign.setValidUntil(request.getValidUntil());
        campaign.setMaxRedemptions(request.getMaxRedemptions());
        campaign.setTicketTypes(resolveTicketTypes(eventId, request.getTicketTypeIds()));
        PromoCampaign saved = campaignRepository.save(campaign);

        auditService.record(organizerId, AuditAction.PROMO_CAMPAIGN_CREATED, saved.getId(), eventId,
                "Promo campaign '" + saved.getName() + "' created");
        return toDtos(List.of(saved)).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromoCampaignResponse> getCampaigns(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        getManagedEvent(eventId, requesterId, isPlatformAdmin);
        return toDtos(campaignRepository.findAllByEventIdOrderByIdDesc(eventId));
    }

    @Override
    @Transactional(readOnly = true)
    public PromoCampaignResponse getCampaign(Long eventId, Long campaignId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        getManagedEvent(eventId, requesterId, isPlatformAdmin);
        return toDtos(List.of(getCampaignOfEvent(eventId, campaignId))).getFirst();
    }

    @Override
    @Transactional
    public PromoCampaignResponse setEnabled(Long organizerId, Long eventId, Long campaignId, boolean enabled) {
        getOwnedEvent(organizerId, eventId);
        PromoCampaign campaign = getCampaignOfEvent(eventId, campaignId);
        if (campaign.isEnabled() != enabled) {
            campaign.setEnabled(enabled);
            auditService.record(organizerId,
                    enabled ? AuditAction.PROMO_CAMPAIGN_ENABLED : AuditAction.PROMO_CAMPAIGN_DISABLED,
                    campaignId, eventId,
                    "Promo campaign '" + campaign.getName() + "' " + (enabled ? "enabled" : "disabled"));
        }
        return toDtos(List.of(campaign)).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getCampaignQr(Long eventId, Long campaignId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        getManagedEvent(eventId, requesterId, isPlatformAdmin);
        return qrCodeGenerator.toCampaignPng(campaignUrl(getCampaignOfEvent(eventId, campaignId)));
    }

    @Override
    @Transactional(readOnly = true)
    public PromoPreviewResponse preview(Long eventId, @Nullable String code, @Nullable String token,
                                        @Nullable Long requesterId, boolean isPlatformAdmin) {
        eventService.getEventById(eventId, requesterId, isPlatformAdmin); // 404 for an event they may not see

        Optional<PromoCampaign> found;
        if (token != null && !token.isBlank()) {
            found = campaignRepository.findByEventIdAndToken(eventId, token.trim());
        } else if (code != null && !code.isBlank()) {
            found = campaignRepository.findByEventIdAndCode(eventId, normalize(code));
        } else {
            throw new BusinessRuleViolationException("Provide a promo code or a campaign token");
        }
        PromoCampaign campaign = found.orElseThrow(PromoCampaignServiceImpl::unknownCode);
        ensureRedeemable(campaign, LocalDateTime.now());

        PromoPreviewResponse dto = new PromoPreviewResponse();
        dto.setCode(campaign.getCode());
        dto.setDiscountType(campaign.getDiscountType());
        dto.setDiscountValue(campaign.getDiscountValue());
        dto.setValidUntil(campaign.getValidUntil());
        dto.setTicketTypeIds(ticketTypeIds(campaign));
        return dto;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PromoCampaign getRedeemableForUpdate(Long eventId, String code, LocalDateTime now) {
        PromoCampaign campaign = campaignRepository.findByEventIdAndCodeForUpdate(eventId, normalize(code))
                .orElseThrow(PromoCampaignServiceImpl::unknownCode);
        ensureRedeemable(campaign, now);
        return campaign;
    }

    @Override
    @Transactional(readOnly = true)
    public PromoCampaign getRedeemable(Long eventId, String code, LocalDateTime now) {
        PromoCampaign campaign = campaignRepository.findByEventIdAndCode(eventId, normalize(code))
                .orElseThrow(PromoCampaignServiceImpl::unknownCode);
        ensureRedeemable(campaign, now);
        return campaign;
    }

    // The only place a discount is ever calculated: the client never sends an amount.
    @Override
    public BigDecimal calculateDiscount(PromoCampaign campaign, List<OrderItem> items) {
        Set<Long> applicable = ticketTypeIds(campaign);
        BigDecimal base = items.stream()
                .filter(item -> applicable.isEmpty() || applicable.contains(item.getTicketType().getId()))
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (base.signum() == 0) {
            throw new BusinessRuleViolationException("This promo code does not apply to the selected tickets");
        }
        return campaign.getDiscountType() == PromoDiscountType.PERCENT
                ? base.multiply(campaign.getDiscountValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : campaign.getDiscountValue().min(base);
    }

    // helpers //

    private void ensureRedeemable(PromoCampaign campaign, LocalDateTime now) {
        if (!campaign.isEnabled()) {
            throw new BusinessRuleViolationException("This promo code has been disabled");
        }
        if (campaign.getValidFrom() != null && now.isBefore(campaign.getValidFrom())) {
            throw new BusinessRuleViolationException("This promo code is not active yet");
        }
        if (campaign.getValidUntil() != null && now.isAfter(campaign.getValidUntil())) {
            throw new BusinessRuleViolationException("This promo code has expired");
        }
        if (campaign.getMaxRedemptions() != null && orderRepository.countActiveRedemptions(
                campaign.getId(), OrderStatus.PAID, OrderStatus.PENDING, now) >= campaign.getMaxRedemptions()) {
            throw new BusinessRuleViolationException("This promo code has reached its redemption limit");
        }
    }

    private static BusinessRuleViolationException unknownCode() {
        return new BusinessRuleViolationException("This promo code is not valid for this event");
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private Set<TicketType> resolveTicketTypes(Long eventId, @Nullable Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }
        Set<TicketType> types = new HashSet<>(ticketTypeRepository.findAllById(ids));
        if (types.size() != ids.size() || types.stream().anyMatch(t -> !t.getEvent().getId().equals(eventId))) {
            throw new BusinessRuleViolationException("Every ticket type must belong to this event");
        }
        return types;
    }

    private static Set<Long> ticketTypeIds(PromoCampaign campaign) {
        return campaign.getTicketTypes().stream().map(TicketType::getId).collect(Collectors.toSet());
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            code = builder.toString();
        } while (campaignRepository.existsByCode(code));
        return code;
    }

    private static String generateToken() {
        byte[] random = new byte[24];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String campaignUrl(PromoCampaign campaign) {
        return frontendUrl.replaceAll("/+$", "") + "/events/" + campaign.getEvent().getId() + "?promo=" + campaign.getToken();
    }

    private Event getOwnedEvent(Long organizerId, Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new ForbiddenOperationException("You are not authorized to modify this event");
        }
        return event;
    }

    // Reads are public at the filter level (GET /api/events/**), so anyone else gets 404.
    private void getManagedEvent(Long eventId, @Nullable Long requesterId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (!isPlatformAdmin && !event.getOrganizer().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
    }

    private PromoCampaign getCampaignOfEvent(Long eventId, Long campaignId) {
        return campaignRepository.findByIdAndEventId(campaignId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign not found with id: " + campaignId));
    }

    private List<PromoCampaignResponse> toDtos(List<PromoCampaign> campaigns) {
        if (campaigns.isEmpty()) {
            return List.of();
        }
        List<Long> ids = campaigns.stream().map(PromoCampaign::getId).toList();
        Map<Long, Object[]> orderStats = orderRepository
                .aggregateByCampaign(ids, OrderStatus.PAID, OrderStatus.PENDING, LocalDateTime.now()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));
        Map<Long, Object[]> itemStats = orderItemRepository.aggregatePaidByCampaign(ids, OrderStatus.PAID).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        return campaigns.stream().map(campaign -> {
            Object[] orders = orderStats.get(campaign.getId());
            Object[] items = itemStats.get(campaign.getId());
            long redemptions = orders != null ? ((Number) orders[1]).longValue() : 0;
            long pending = orders != null ? ((Number) orders[3]).longValue() : 0;
            BigDecimal discount = orders != null ? money(orders[2]) : money(null);
            BigDecimal gross = items != null ? money(items[2]) : money(null);

            PromoCampaignResponse dto = new PromoCampaignResponse();
            dto.setId(campaign.getId());
            dto.setEventId(campaign.getEvent().getId());
            dto.setName(campaign.getName());
            dto.setCode(campaign.getCode());
            dto.setCampaignUrl(campaignUrl(campaign));
            dto.setDiscountType(campaign.getDiscountType());
            dto.setDiscountValue(campaign.getDiscountValue());
            dto.setValidFrom(campaign.getValidFrom());
            dto.setValidUntil(campaign.getValidUntil());
            dto.setMaxRedemptions(campaign.getMaxRedemptions());
            dto.setEnabled(campaign.isEnabled());
            dto.setTicketTypeIds(ticketTypeIds(campaign));
            dto.setCreatedAt(campaign.getCreatedAt());
            dto.setRedemptions(redemptions);
            dto.setPendingRedemptions(pending);
            dto.setRemainingRedemptions(campaign.getMaxRedemptions() != null
                    ? Math.max(campaign.getMaxRedemptions() - redemptions - pending, 0) : null);
            dto.setTicketsSold(items != null ? ((Number) items[1]).longValue() : 0);
            dto.setGrossRevenue(gross);
            dto.setDiscountAmount(discount);
            dto.setNetRevenue(gross.subtract(discount));
            return dto;
        }).toList();
    }

    private static BigDecimal money(@Nullable Object value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
