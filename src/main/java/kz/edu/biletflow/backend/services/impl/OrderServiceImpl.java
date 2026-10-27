package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.CreateOrderRequest;
import kz.edu.biletflow.backend.dtos.OrderItemRequest;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.OrderMapper;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;

    @Value("${app.orders.hold-minutes:15}")
    private int holdMinutes;

    @Override
    @Transactional
    public OrderResponse createOrder(Long userId, boolean isPlatformAdmin, CreateOrderRequest request) {
        Event event = getPurchasableEvent(request.getEventId(), userId, isPlatformAdmin);
        LocalDateTime now = LocalDateTime.now();
        validateRegistrationWindow(event, now);

        Set<Long> seen = new HashSet<>();
        for (OrderItemRequest item : request.getItems()) {
            if (!seen.add(item.getTicketTypeId())) {
                throw new BusinessRuleViolationException("Ticket type " + item.getTicketTypeId() + " is listed more than once");
            }
        }

        Order order = new Order();
        order.setUser(userRepository.getReferenceById(userId));
        order.setEvent(event);
        order.setExpiresAt(now.plusMinutes(holdMinutes));

        // Lock ticket types in id order so two concurrent orders can't deadlock each other.
        List<OrderItemRequest> sortedItems = request.getItems().stream()
                .sorted(Comparator.comparing(OrderItemRequest::getTicketTypeId))
                .toList();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : sortedItems) {
            TicketType ticketType = ticketTypeRepository.findByIdForUpdate(itemRequest.getTicketTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Ticket type not found with id: " + itemRequest.getTicketTypeId()));
            validateTicketTypeForPurchase(ticketType, event, itemRequest.getQuantity(), now);

            OrderItem item = new OrderItem();
            item.setTicketType(ticketType);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(ticketType.getPrice());
            order.addItem(item);

            total = total.add(ticketType.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        }
        order.setTotalAmount(total);

        // Nothing to pay for a free order: confirm it right away.
        boolean free = total.signum() == 0;
        if (free) {
            markPaid(order, now);
        }

        Order saved = orderRepository.save(order);
        if (free) {
            issueTickets(saved); // after save: tickets reference the persisted order
        }
        return orderMapper.toDto(saved);
    }

    @Override
    @Transactional
    public OrderResponse payOrder(Long userId, Long orderId) {
        Order order = getOwnedOrder(userId, orderId);
        LocalDateTime now = LocalDateTime.now();

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleViolationException("Only a pending order can be paid, this order is " + order.getStatus());
        }
        if (!order.getExpiresAt().isAfter(now)) {
            // The scheduler flips it to EXPIRED (a status change here would be rolled back by the exception).
            throw new BusinessRuleViolationException("The order has expired, please create a new one");
        }
        if (order.getEvent().getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("The event is no longer available");
        }

        // TODO (payments): call a real payment provider here; for now payment always succeeds.
        markPaid(order, now);
        Order saved = orderRepository.save(order);
        issueTickets(saved);
        return orderMapper.toDto(saved);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long userId, Long orderId) {
        Order order = getOwnedOrder(userId, orderId);
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
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        if (!isPlatformAdmin && !order.getUser().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        return orderMapper.toDto(order);
    }

    @Override
    @Transactional
    public int expireStaleOrders() {
        return orderRepository.expirePending(OrderStatus.PENDING, OrderStatus.EXPIRED, LocalDateTime.now());
    }

    // helpers //

    private void markPaid(Order order, LocalDateTime now) {
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(now);
    }

    private void issueTickets(Order order) {
        for (OrderItem item : order.getItems()) {
            for (int i = 0; i < item.getQuantity(); i++) {
                Ticket ticket = new Ticket();
                ticket.setCode(UUID.randomUUID().toString());
                ticket.setOrder(order);
                ticket.setEvent(order.getEvent());
                ticket.setTicketType(item.getTicketType());
                ticket.setOwner(order.getUser());
                ticketRepository.save(ticket);
            }
        }
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

    private Order getOwnedOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        // Someone else's order is reported as missing, so its existence isn't revealed.
        if (!order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Order not found with id: " + orderId);
        }
        return order;
    }
}
