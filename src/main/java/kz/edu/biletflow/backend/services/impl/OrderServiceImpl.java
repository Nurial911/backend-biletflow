package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.CreateOrderRequest;
import kz.edu.biletflow.backend.dtos.OrderItemRequest;
import kz.edu.biletflow.backend.dtos.OrderQuoteResponse;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PaymentResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.PaymentDeclinedException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.exception.SeatUnavailableException;
import kz.edu.biletflow.backend.mappers.SeatLabels;
import kz.edu.biletflow.backend.mappers.OrderMapper;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.AuditService;
import kz.edu.biletflow.backend.services.NotificationService;
import kz.edu.biletflow.backend.services.OrderService;
import kz.edu.biletflow.backend.services.PromoCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private static final String DECLINED_REASON = "Simulated payment declined";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;
    private final OrderItemRepository orderItemRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final PromoCampaignService promoCampaignService;
    private final NotificationService notificationService;
    private final OrderFulfillment fulfillment;
    private final EventSeatRepository seatRepository;
    private final EventSectionRepository sectionRepository;

    @Value("${app.orders.hold-minutes:15}")
    private int holdMinutes;

    @Value("${app.payments.provider:simulated}")
    private String paymentProvider;

    @Override
    @Transactional
    public OrderResponse createOrder(Long userId, boolean isPlatformAdmin, CreateOrderRequest request) {
        Event event = getPurchasableEvent(request.getEventId(), userId, isPlatformAdmin);
        LocalDateTime now = LocalDateTime.now();
        validateRegistrationWindow(event, now);

        User buyer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Order order = new Order();
        order.setUser(buyer);
        order.setAttendeeName(resolveAttendeeName(request.getAttendeeName(), buyer));
        order.setEvent(event);
        order.setExpiresAt(now.plusMinutes(holdMinutes));

        // Locked before the ticket types, in the same order on every request, so orders can't deadlock.
        PromoCampaign campaign = hasPromoCode(request)
                ? promoCampaignService.getRedeemableForUpdate(event.getId(), request.getPromoCode(), now)
                : null;

        // Locks the ticket types, then the seats, each in id order, so two concurrent orders can't deadlock.
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : buildItems(event, request, now, true)) {
            order.addItem(item);
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        if (campaign != null) {
            BigDecimal discount = promoCampaignService.calculateDiscount(campaign, order.getItems());
            order.setPromoCampaign(campaign);
            order.setDiscountAmount(discount);
            total = total.subtract(discount);
        }
        order.setTotalAmount(total);

        // Nothing to pay for a free order: confirm it right away.
        boolean free = total.signum() == 0;
        if (free) {
            fulfillment.markPaid(order, now);
        }

        Order saved = orderRepository.save(order);
        if (free) {
            fulfillment.issueTickets(saved, false); // after save: tickets reference the persisted order
        }
        return orderMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderQuoteResponse quoteOrder(Long userId, boolean isPlatformAdmin, CreateOrderRequest request) {
        Event event = getPurchasableEvent(request.getEventId(), userId, isPlatformAdmin);
        LocalDateTime now = LocalDateTime.now();
        validateRegistrationWindow(event, now);

        List<OrderItem> items = buildItems(event, request, now, false);
        BigDecimal subtotal = items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderQuoteResponse quote = new OrderQuoteResponse();
        BigDecimal discount = BigDecimal.ZERO;
        if (hasPromoCode(request)) {
            PromoCampaign campaign = promoCampaignService.getRedeemable(event.getId(), request.getPromoCode(), now);
            discount = promoCampaignService.calculateDiscount(campaign, items);
            quote.setPromoCode(campaign.getCode());
        }
        quote.setSubtotalAmount(subtotal);
        quote.setDiscountAmount(discount);
        quote.setTotalAmount(subtotal.subtract(discount));
        return quote;
    }

    @Override
    @Transactional(noRollbackFor = PaymentDeclinedException.class) // the FAILED payment must be kept
    public OrderResponse payOrder(Long userId, Long orderId, SimulatedPaymentOutcome outcome) {
        // With a real bank this endpoint would let buyers mark their own orders as paid.
        if (!"simulated".equalsIgnoreCase(paymentProvider)) {
            throw new BusinessRuleViolationException("Payments go through the bank here: start a payment session instead");
        }
        Order order = getOwnedOrderForUpdate(userId, orderId);
        LocalDateTime now = LocalDateTime.now();

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleViolationException("Only a pending order can be paid, this order is " + order.getStatus());
        }
        if (!order.getExpiresAt().isAfter(now)) {
            // The scheduler flips it to EXPIRED (a status change here would be rolled back by the exception).
            throw new BusinessRuleViolationException("The order has expired, please create a new one");
        }
        // Suspend cancels PENDING orders in bulk; this also catches one created concurrently with it.
        if (order.getEvent().getStatus() != EventStatus.PUBLISHED || order.getEvent().isSuspended()) {
            throw new BusinessRuleViolationException("The event is no longer available");
        }
        if (!order.getEvent().isPaidSalesActive()) {
            throw new BusinessRuleViolationException("Paid sales for this event are suspended");
        }

        // Simulation only (SRS 3.2): no provider is called and no money moves. A real or sandbox
        // provider would be called here, and its answer would replace the requested outcome.
        Long eventId = order.getEvent().getId();
        if (outcome == SimulatedPaymentOutcome.DECLINED) {
            Payment failed = fulfillment.savePayment(order, simulatedPayment(PaymentStatus.FAILED, BigDecimal.ZERO, DECLINED_REASON));
            auditService.record(userId, AuditAction.PAYMENT_FAILED, failed.getId(), eventId,
                    "Simulated payment of " + order.getTotalAmount() + " KZT declined for order #" + order.getId());
            notificationService.notifyUser(userId, NotificationType.PAYMENT_FAILED,
                    "Payment failed for " + order.getEvent().getTitle(),
                    "The payment for order #" + order.getId() + " was declined, so no tickets were issued."
                            + " You can try again until the reservation expires."
                            + "\n\nThis is a demonstration payment: no real money is involved.", null);
            throw new PaymentDeclinedException(DECLINED_REASON + ", no tickets were issued. You can try again until the hold expires.");
        }

        fulfillment.markPaid(order, now);
        Order saved = orderRepository.save(order);
        Payment payment = fulfillment.savePayment(saved,
                simulatedPayment(PaymentStatus.SUCCEEDED, fulfillment.processingFee(saved.getTotalAmount()), null));
        fulfillment.issueTickets(saved, true);
        auditService.record(userId, AuditAction.PAYMENT_SUCCEEDED, payment.getId(), eventId,
                "Simulated payment of " + saved.getTotalAmount() + " KZT for order #" + saved.getId());
        return orderMapper.toDto(saved);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = getOwnedOrderForUpdate(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleViolationException("Only a pending order can be cancelled, this order is " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        return orderMapper.toDto(orderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Long userId, Pageable pageable) {
        return orderRepository.findAllByUserId(userId, pageable).map(orderMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId, Long requesterId, boolean isPlatformAdmin) {
        return orderMapper.toDto(getViewableOrder(orderId, requesterId, isPlatformAdmin));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getOrderPayments(Long orderId, Long requesterId, boolean isPlatformAdmin) {
        getViewableOrder(orderId, requesterId, isPlatformAdmin);
        return paymentRepository.findAllByOrderIdOrderByIdAsc(orderId).stream()
                .map(orderMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public int expireStaleOrders() {
        return orderRepository.expirePending(OrderStatus.PENDING, OrderStatus.EXPIRED, LocalDateTime.now());
    }

    // helpers //

    private OrderFulfillment.PaymentDetails simulatedPayment(PaymentStatus status, BigDecimal fee, String failureReason) {
        return new OrderFulfillment.PaymentDetails("SIM-" + UUID.randomUUID(), status, fee, failureReason, "SIMULATED", null, true);
    }

    private String resolveAttendeeName(String requested, User buyer) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (buyer.getFullName() != null && !buyer.getFullName().isBlank()) {
            return buyer.getFullName().trim();
        }
        return buyer.getEmail();
    }

    // The order's items, validated: one per ticket type for quantities, one per seat (quantity 1) for seats.
    // With lock, the ticket types are row-locked in id order and then the seats in id order (SeatingServiceImpl
    // takes the same locks in the same order), and the seats are checked again once locked.
    private List<OrderItem> buildItems(Event event, CreateOrderRequest request, LocalDateTime now, boolean lock) {
        List<OrderItemRequest> itemRequests = request.getItems() != null ? request.getItems() : List.of();
        List<Long> seatIds = request.getSeatIds() != null ? request.getSeatIds() : List.of();
        if (itemRequests.isEmpty() && seatIds.isEmpty()) {
            throw new BusinessRuleViolationException("Order must contain at least one item");
        }
        rejectRepeatedTicketTypes(itemRequests);
        boolean assigned = event.getSeatingMode() == SeatingMode.ASSIGNED;
        if (!seatIds.isEmpty() && !assigned) {
            throw new BusinessRuleViolationException("This event has general admission: order ticket types, not seats");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new BusinessRuleViolationException("A seat is listed more than once");
        }

        List<EventSeat> seats = new ArrayList<>(seatIds.isEmpty() ? List.of() : seatRepository.findAllForEvent(event.getId(), seatIds));
        if (seats.size() != seatIds.size()) {
            Set<Long> found = seats.stream().map(EventSeat::getId).collect(Collectors.toSet());
            Long missing = seatIds.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
            throw new ResourceNotFoundException("Seat not found with id: " + missing);
        }
        seats.sort(Comparator.comparing(EventSeat::getId));

        // Tickets per ticket type: the requested quantities plus one per seat of a section on sale.
        Map<Long, Integer> quantities = new TreeMap<>();
        itemRequests.forEach(r -> quantities.merge(r.getTicketTypeId(), r.getQuantity(), Integer::sum));
        for (EventSeat seat : seats) {
            TicketType type = seat.getSection().getTicketType();
            if (type != null) {
                quantities.merge(type.getId(), 1, Integer::sum);
            }
        }

        Map<Long, TicketType> ticketTypes = new HashMap<>();
        for (Long typeId : quantities.keySet()) { // TreeMap: id order
            ticketTypes.put(typeId, (lock ? ticketTypeRepository.findByIdForUpdate(typeId) : ticketTypeRepository.findById(typeId))
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found with id: " + typeId)));
        }

        // With assigned seating, a ticket type ordered by count must sell a standing section.
        Map<Long, EventSection> standingByType = new HashMap<>();
        if (assigned && !itemRequests.isEmpty()) {
            Map<Long, List<EventSection>> sectionsByType = sectionRepository.findAllByEventIdOrderBySortOrderAsc(event.getId()).stream()
                    .filter(s -> s.getTicketType() != null)
                    .collect(Collectors.groupingBy(s -> s.getTicketType().getId()));
            for (OrderItemRequest itemRequest : itemRequests) {
                TicketType ticketType = ticketTypes.get(itemRequest.getTicketTypeId());
                List<EventSection> sections = sectionsByType.getOrDefault(ticketType.getId(), List.of());
                EventSection standing = sections.stream().filter(s -> s.getKind() == SectionKind.STANDING).findFirst().orElse(null);
                if (standing == null) {
                    throw new BusinessRuleViolationException(sections.isEmpty()
                            ? "'" + ticketType.getName() + "' is not on the seat map"
                            : "Pick seats on the seat map for '" + ticketType.getName() + "'");
                }
                standingByType.put(ticketType.getId(), standing);
            }
        }
        if (!seats.isEmpty()) {
            if (lock) {
                seatRepository.lockAll(seatIds);
            }
            ensureSeatsAvailable(seats, now);
        }
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            validateTicketTypeForPurchase(ticketTypes.get(entry.getKey()), event, entry.getValue(), now);
        }

        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest itemRequest : itemRequests.stream().sorted(Comparator.comparing(OrderItemRequest::getTicketTypeId)).toList()) {
            TicketType ticketType = ticketTypes.get(itemRequest.getTicketTypeId());
            OrderItem item = newItem(ticketType, itemRequest.getQuantity());
            EventSection standing = standingByType.get(ticketType.getId());
            if (standing != null) {
                item.setEventSection(standing);
                item.setSectionName(standing.getName());
            }
            items.add(item);
        }
        for (EventSeat seat : seats) {
            EventSection section = seat.getSection();
            OrderItem item = newItem(ticketTypes.get(section.getTicketType().getId()), 1);
            item.setEventSection(section);
            item.setEventSeat(seat);
            item.setSectionName(section.getName());
            item.setRowLabel(seat.getRowLabel());
            item.setSeatNumber(seat.getSeatNumber());
            items.add(item);
        }
        return items;
    }

    // Read from the database after the seat locks: a seat must still be on sale, at the ticket type this
    // order uses, and not in another paid or unexpired order. Otherwise 409, and the client refreshes the map.
    private void ensureSeatsAvailable(List<EventSeat> seats, LocalDateTime now) {
        List<Long> ids = seats.stream().map(EventSeat::getId).toList();
        Map<Long, Object[]> states = new HashMap<>();
        for (Object[] row : seatRepository.findStates(ids)) {
            states.put((Long) row[0], row);
        }
        Set<Long> claimed = orderItemRepository.findSeatClaims(ids, OrderStatus.PAID, OrderStatus.PENDING, now).stream()
                .map(row -> (Long) row[0])
                .collect(Collectors.toSet());
        List<String> unavailable = new ArrayList<>();
        for (EventSeat seat : seats) {
            Object[] state = states.get(seat.getId());
            TicketType readType = seat.getSection().getTicketType();
            boolean onSale = state != null && !Boolean.TRUE.equals(state[1]) && state[2] != null
                    && !Boolean.TRUE.equals(state[3])
                    && readType != null && readType.getId().equals(state[2]);
            if (!onSale || claimed.contains(seat.getId())) {
                unavailable.add(SeatLabels.describe(seat.getSection().getName(), seat.getRowLabel(), seat.getSeatNumber()));
            }
        }
        if (!unavailable.isEmpty()) {
            throw new SeatUnavailableException((unavailable.size() == 1 ? "This seat is" : "These seats are")
                    + " no longer available: " + String.join("; ", unavailable) + ". Please pick other seats.");
        }
    }

    private static OrderItem newItem(TicketType ticketType, int quantity) {
        OrderItem item = new OrderItem();
        item.setTicketType(ticketType);
        item.setQuantity(quantity);
        item.setUnitPrice(ticketType.getPrice());
        return item;
    }

    private void rejectRepeatedTicketTypes(List<OrderItemRequest> items) {
        Set<Long> seen = new HashSet<>();
        for (OrderItemRequest item : items) {
            if (!seen.add(item.getTicketTypeId())) {
                throw new BusinessRuleViolationException("Ticket type " + item.getTicketTypeId() + " is listed more than once");
            }
        }
    }

    private boolean hasPromoCode(CreateOrderRequest request) {
        return request.getPromoCode() != null && !request.getPromoCode().isBlank();
    }

    private Event getPurchasableEvent(Long eventId, Long userId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        boolean canSeePrivate = isPlatformAdmin || event.getOrganizer().getId().equals(userId);
        // Same rule as getEventById: don't reveal that an invisible event exists.
        if (event.getStatus() != EventStatus.PUBLISHED
                || (event.getVisibilityStatus() == VisibilityStatus.PRIVATE && !canSeePrivate)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        if (event.isSuspended()) {
            // Owner/admin already know the event exists; everyone else gets the usual 404.
            if (canSeePrivate) {
                throw new BusinessRuleViolationException("Sales are stopped: this event is suspended");
            }
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        return event;
    }

    private void validateRegistrationWindow(Event event, LocalDateTime now) {
        if (event.getRegistrationOpeningTime() != null && now.isBefore(event.getRegistrationOpeningTime())) {
            throw new BusinessRuleViolationException("Registration for this event has not opened yet");
        }
        if (event.getRegistrationClosingTime() != null && now.isAfter(event.getRegistrationClosingTime())) {
            throw new BusinessRuleViolationException("Registration for this event is closed");
        }
        if (!now.isBefore(event.getStartTime())) {
            throw new BusinessRuleViolationException("The event has already started");
        }
    }

    private void validateTicketTypeForPurchase(TicketType ticketType, Event event, int quantity, LocalDateTime now) {
        if (!ticketType.getEvent().getId().equals(event.getId())) {
            throw new BusinessRuleViolationException(
                    "Ticket type " + ticketType.getId() + " does not belong to this event");
        }
        if (ticketType.isHidden()) {
            throw new ResourceNotFoundException("Ticket type not found with id: " + ticketType.getId());
        }
        if (ticketType.getPrice().signum() > 0 && !event.isPaidSalesActive()) {
            throw new BusinessRuleViolationException("Paid tickets for this event are not on sale yet");
        }
        if (ticketType.getSalesStart() != null && now.isBefore(ticketType.getSalesStart())) {
            throw new BusinessRuleViolationException("Sales of '" + ticketType.getName() + "' have not started yet");
        }
        if (ticketType.getSalesEnd() != null && now.isAfter(ticketType.getSalesEnd())) {
            throw new BusinessRuleViolationException("Sales of '" + ticketType.getName() + "' have ended");
        }
        if (quantity > ticketType.getMaxPerOrder()) {
            throw new BusinessRuleViolationException(
                    "At most " + ticketType.getMaxPerOrder() + " tickets of '" + ticketType.getName() + "' per order");
        }
        long taken = orderItemRepository.countSoldOrReserved(
                ticketType.getId(), OrderStatus.PAID, OrderStatus.PENDING, now);
        long available = ticketType.getQuantity() - taken;
        if (quantity > available) {
            throw new BusinessRuleViolationException(
                    "Not enough '" + ticketType.getName() + "' tickets left. Available: " + Math.max(available, 0));
        }
    }

    // Someone else's order is reported as missing, so its existence isn't revealed.
    private Order getViewableOrder(Long orderId, Long requesterId, boolean isPlatformAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        if (!isPlatformAdmin && !order.getUser().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        return order;
    }

    // Locked until the transaction ends, so pay and cancel on one order run one after the other.
    private Order getOwnedOrderForUpdate(Long userId, Long orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        // Someone else's order is reported as missing, so its existence isn't revealed.
        if (!order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        return order;
    }
}
