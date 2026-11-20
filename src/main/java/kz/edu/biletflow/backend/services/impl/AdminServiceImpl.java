package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import kz.edu.biletflow.backend.dtos.AdminOrderResponse;
import kz.edu.biletflow.backend.dtos.AdminOverviewResponse;
import kz.edu.biletflow.backend.dtos.AdminPaymentResponse;
import kz.edu.biletflow.backend.dtos.AdminRefundResponse;
import kz.edu.biletflow.backend.dtos.EventResponse;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.EventReport;
import kz.edu.biletflow.backend.entities.EventStatus;
import kz.edu.biletflow.backend.entities.Order;
import kz.edu.biletflow.backend.entities.OrderItem;
import kz.edu.biletflow.backend.entities.OrderStatus;
import kz.edu.biletflow.backend.entities.OrganizerVerificationStatus;
import kz.edu.biletflow.backend.entities.PaidSalesStatus;
import kz.edu.biletflow.backend.entities.Payment;
import kz.edu.biletflow.backend.entities.PaymentStatus;
import kz.edu.biletflow.backend.entities.Refund;
import kz.edu.biletflow.backend.entities.RefundStatus;
import kz.edu.biletflow.backend.entities.SupportCaseStatus;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.mappers.EventMapper;
import kz.edu.biletflow.backend.repositories.EventReportRepository;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.OrderRepository;
import kz.edu.biletflow.backend.repositories.OrganizerProfileRepository;
import kz.edu.biletflow.backend.repositories.PaidSalesActivationRepository;
import kz.edu.biletflow.backend.repositories.PaymentRepository;
import kz.edu.biletflow.backend.repositories.RefundRepository;
import kz.edu.biletflow.backend.repositories.SupportCaseRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final EventReportRepository eventReportRepository;
    private final SupportCaseRepository supportCaseRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final PaidSalesActivationRepository activationRepository;
    private final EventMapper eventMapper;

    @Override
    public AdminOverviewResponse getOverview() {
        LocalDateTime now = LocalDateTime.now();
        AdminOverviewResponse dto = new AdminOverviewResponse();
        dto.setGeneratedAt(now);
        dto.setUsers(userRepository.count());
        dto.setSuspendedUsers(userRepository.countBySuspendedTrue());
        dto.setPendingOrganizerVerifications(
                organizerProfileRepository.countByVerificationStatus(OrganizerVerificationStatus.PENDING));
        dto.setPublishedEvents(eventRepository.countByStatus(EventStatus.PUBLISHED));
        dto.setSuspendedEvents(eventRepository.countBySuspendedTrue());
        dto.setOpenEventReports(eventReportRepository.countByStatus(EventReport.Status.OPEN));
        dto.setSuspendedPaidSales(activationRepository.countByStatus(PaidSalesStatus.SUSPENDED));
        dto.setFailedPaymentsLast24Hours(
                paymentRepository.countByStatusAndCreatedAtGreaterThanEqual(PaymentStatus.FAILED, now.minusHours(24)));
        dto.setFailedPaymentsLast7Days(
                paymentRepository.countByStatusAndCreatedAtGreaterThanEqual(PaymentStatus.FAILED, now.minusDays(7)));
        dto.setPendingRefunds(refundRepository.countByStatus(RefundStatus.PENDING));
        dto.setFailedRefundsLast7Days(
                refundRepository.countByStatusAndCreatedAtGreaterThanEqual(RefundStatus.FAILED, now.minusDays(7)));
        dto.setEscalatedSupportCases(supportCaseRepository.countByEscalatedTrueAndStatusNot(SupportCaseStatus.RESOLVED));
        return dto;
    }

    @Override
    public Page<EventResponse> searchEvents(@Nullable String q, @Nullable EventStatus status, @Nullable Boolean suspended,
                                            @Nullable Long organizerId, Pageable pageable) {
        String text = SearchText.normalize(q);
        Specification<Event> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (text != null) {
                predicates.add(matches(cb, text, root.get("id"),
                        root.get("title"), root.get("organizer").get("email")));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (suspended != null) {
                predicates.add(cb.equal(root.get("suspended"), suspended));
            }
            if (organizerId != null) {
                predicates.add(cb.equal(root.get("organizer").get("id"), organizerId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return eventRepository.findAll(spec, pageable).map(eventMapper::toDto);
    }

    @Override
    public Page<AdminOrderResponse> searchOrders(@Nullable String q, @Nullable OrderStatus status, @Nullable Long eventId,
                                                 Pageable pageable) {
        String text = SearchText.normalize(q);
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (text != null) {
                predicates.add(matches(cb, text, root.get("id"),
                        root.get("user").get("email"), root.get("attendeeName")));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (eventId != null) {
                predicates.add(cb.equal(root.get("event").get("id"), eventId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return orderRepository.findAll(spec, pageable).map(AdminServiceImpl::toOrderDto);
    }

    @Override
    public Page<AdminPaymentResponse> searchPayments(@Nullable String q, @Nullable PaymentStatus status,
                                                     @Nullable String provider, @Nullable LocalDate from,
                                                     @Nullable LocalDate to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleViolationException("'from' must not be after 'to'");
        }
        String text = SearchText.normalize(q);
        String providerName = provider == null || provider.isBlank() ? null : provider.trim().toUpperCase(Locale.ROOT);
        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (text != null) {
                predicates.add(matches(cb, text, root.get("order").get("id"),
                        root.get("reference"), root.get("order").get("user").get("email")));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (providerName != null) {
                predicates.add(cb.equal(root.get("provider"), providerName));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return paymentRepository.findAll(spec, pageable).map(AdminServiceImpl::toPaymentDto);
    }

    @Override
    public Page<AdminRefundResponse> searchRefunds(@Nullable String q, @Nullable RefundStatus status, Pageable pageable) {
        String text = SearchText.normalize(q);
        Specification<Refund> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (text != null) {
                predicates.add(matches(cb, text, root.get("order").get("id"),
                        root.get("reference"), root.get("order").get("user").get("email")));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return refundRepository.findAll(spec, pageable).map(AdminServiceImpl::toRefundDto);
    }

    // helpers //

    // Any of the text columns contains q, or q is the id.
    @SafeVarargs
    private static Predicate matches(CriteriaBuilder cb, String text, Path<Long> id, Path<String>... columns) {
        String pattern = SearchText.containsPattern(text);
        List<Predicate> any = new ArrayList<>();
        for (Path<String> column : columns) {
            Expression<String> lower = cb.lower(column);
            any.add(cb.like(lower, pattern, SearchText.ESCAPE));
        }
        Long asId = SearchText.asId(text);
        if (asId != null) {
            any.add(cb.equal(id, asId));
        }
        return cb.or(any.toArray(Predicate[]::new));
    }

    private static AdminOrderResponse toOrderDto(Order order) {
        AdminOrderResponse dto = new AdminOrderResponse();
        dto.setId(order.getId());
        dto.setEventId(order.getEvent().getId());
        dto.setEventTitle(order.getEvent().getTitle());
        dto.setStatus(order.getStatus());
        dto.setBuyerId(order.getUser().getId());
        dto.setBuyerEmail(order.getUser().getEmail());
        dto.setAttendeeName(order.getAttendeeName());
        dto.setTicketCount(order.getItems().stream().mapToInt(OrderItem::getQuantity).sum());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPromoCode(order.getPromoCampaign() != null ? order.getPromoCampaign().getCode() : null);
        dto.setCreatedAt(order.getCreatedAt());
        dto.setExpiresAt(order.getExpiresAt());
        dto.setPaidAt(order.getPaidAt());
        return dto;
    }

    private static AdminPaymentResponse toPaymentDto(Payment payment) {
        Order order = payment.getOrder();
        AdminPaymentResponse dto = new AdminPaymentResponse();
        dto.setId(payment.getId());
        dto.setReference(payment.getReference());
        dto.setStatus(payment.getStatus());
        dto.setAmount(payment.getAmount());
        dto.setProcessingFee(payment.getProcessingFee());
        dto.setProvider(payment.getProvider());
        dto.setSimulated(payment.isSimulated());
        dto.setFailureReason(payment.getFailureReason());
        dto.setCreatedAt(payment.getCreatedAt());
        dto.setOrderId(order.getId());
        dto.setEventId(order.getEvent().getId());
        dto.setEventTitle(order.getEvent().getTitle());
        dto.setBuyerEmail(order.getUser().getEmail());
        return dto;
    }

    private static AdminRefundResponse toRefundDto(Refund refund) {
        Order order = refund.getOrder();
        AdminRefundResponse dto = new AdminRefundResponse();
        dto.setId(refund.getId());
        dto.setReference(refund.getReference());
        dto.setStatus(refund.getStatus());
        dto.setAmount(refund.getAmount());
        dto.setProvider(refund.getProvider());
        dto.setSimulated(refund.isSimulated());
        dto.setReason(refund.getReason());
        dto.setFailureReason(refund.getFailureReason());
        dto.setInitiatedById(refund.getInitiatedById());
        dto.setCreatedAt(refund.getCreatedAt());
        dto.setCompletedAt(refund.getCompletedAt());
        dto.setOrderId(order.getId());
        dto.setEventId(order.getEvent().getId());
        dto.setEventTitle(order.getEvent().getTitle());
        dto.setBuyerEmail(order.getUser().getEmail());
        return dto;
    }
}
