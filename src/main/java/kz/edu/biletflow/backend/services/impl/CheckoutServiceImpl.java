package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.*;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ForbiddenOperationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.CheckoutService;
import kz.edu.biletflow.backend.services.QrCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {
    private static final int RESERVATION_MINUTES = 10;
    private final UserRepository userRepository;
    private final AttendeeRepository attendeeRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final PaidSalesActivationRepository activationRepository;
    private final InventoryReservationRepository reservationRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final AuditLogRepository auditLogRepository;
    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;

    @Override
    @Transactional
    public OrderResponse createCheckout(Long userId, Long eventId, CheckoutRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (event.getStatus() != EventStatus.PUBLISHED || activationRepository.findByEventId(eventId)
                .map(a -> a.getStatus() != ActivationStatus.ACTIVE).orElse(true)) {
            throw new BusinessRuleViolationException("Paid sales are not active for this event");
        }
        reservationRepository.expireReservations(LocalDateTime.now(), ReservationStatus.EXPIRED);
        TicketType type = ticketTypeRepository.findByIdForUpdate(request.getTicketTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found with id: " + request.getTicketTypeId()));
        if (!type.getEvent().getId().equals(eventId) || type.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("This paid ticket type is not available for the event");
        }
        if (request.getQuantity() > type.getLimitPerOrder()) {
            throw new BusinessRuleViolationException("Requested quantity exceeds the per-order limit");
        }
        validateSalesWindow(type);
        long issued = ticketRepository.countByTicketTypeIdAndStatusIn(type.getId(), List.of(TicketStatus.VALID, TicketStatus.CHECKED_IN));
        long reserved = reservationRepository.countActiveQuantity(type.getId(), LocalDateTime.now());
        if (issued + reserved + request.getQuantity() > type.getQuantity()) {
            throw new BusinessRuleViolationException("Ticket inventory is exhausted");
        }
        Attendee attendee = attendeeRepository.findByUserId(userId).orElseGet(() -> {
            User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
            Attendee newAttendee = new Attendee();
            newAttendee.setUser(user);
            return attendeeRepository.save(newAttendee);
        });
        Order order = new Order();
        order.setAttendee(attendee);
        order.setEvent(event);
        order.setTotalAmount(type.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
        order.setProcessingCharges(BigDecimal.ZERO);
        order.setStatus(OrderStatus.PENDING);
        Order saved = orderRepository.save(order);
        OrderItem item = new OrderItem();
        item.setOrder(saved);
        item.setTicketType(type);
        item.setPrice(type.getPrice());
        item.setQuantity(request.getQuantity());
        saved.getItems().add(item);
        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrder(saved);
        reservation.setTicketType(type);
        reservation.setQuantity(request.getQuantity());
        reservation.setExpiresAt(LocalDateTime.now().plusMinutes(RESERVATION_MINUTES));
        reservationRepository.save(reservation);
        return toResponse(saved, List.of(), reservation.getExpiresAt());
    }

    @Override
    @Transactional
    public OrderResponse pay(Long userId, Long orderId, PaymentRequest request) {
        Order order = ownedOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.PENDING) throw new BusinessRuleViolationException("Order is not payable");
        InventoryReservation reservation = reservationRepository.findActiveByOrderId(orderId)
                .orElseThrow(() -> new BusinessRuleViolationException("Order reservation is no longer active"));
        if (reservation.getExpiresAt().isBefore(LocalDateTime.now())) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            throw new BusinessRuleViolationException("Order reservation has expired");
        }
        Payment payment = paymentRepository.findByOrderId(orderId).orElseGet(Payment::new);
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        if (!request.isSuccessful()) {
            payment.setStatus(PaymentStatus.FAILED);
            reservation.setStatus(ReservationStatus.RELEASED);
            order.setStatus(OrderStatus.CANCELLED);
            paymentRepository.save(payment);
            orderRepository.save(order);
            return toResponse(order, List.of(), null);
        }
        payment.setStatus(PaymentStatus.SUCCEEDED);
        order.setStatus(OrderStatus.PAID);
        Payment savedPayment = paymentRepository.save(payment);
        reservation.setStatus(ReservationStatus.CONVERTED);
        List<Ticket> tickets = issueTickets(order, reservation.getTicketType(), reservation.getQuantity());
        orderRepository.save(order);
        return toResponse(order, tickets, null);
    }

    @Override
    @Transactional
    public OrderResponse refund(Long userId, Long orderId) {
        Order order = ownedOrder(userId, orderId);
        if (order.getStatus() != OrderStatus.PAID) throw new BusinessRuleViolationException("Only paid orders can be refunded");
        order.setStatus(OrderStatus.REFUNDED);
        List<Ticket> tickets = ticketRepository.findAllByOrderId(orderId);
        tickets.forEach(ticket -> ticket.setStatus(TicketStatus.REFUNDED));
        ticketRepository.saveAll(tickets);
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        Refund refund = new Refund();
        refund.setOrder(order);
        refund.setAmount(order.getTotalAmount());
        refund.setStatus(RefundStatus.COMPLETED);
        refundRepository.save(refund);
        AuditLog audit = new AuditLog();
        audit.setActingUser(order.getAttendee().getUser());
        audit.setActionType("REFUND_COMPLETED");
        audit.setAffectedEntity("Order:" + orderId);
        audit.setShortDescription("Full refund completed and tickets invalidated");
        auditLogRepository.save(audit);
        orderRepository.save(order);
        return toResponse(order, tickets, null);
    }

    private Order ownedOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!order.getAttendee().getUser().getId().equals(userId)) throw new ForbiddenOperationException("You are not authorized to access this order");
        return order;
    }

    private List<Ticket> issueTickets(Order order, TicketType type, int quantity) {
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            Ticket ticket = new Ticket();
            ticket.setTicketIdentifier(UUID.randomUUID().toString());
            ticket.setQrCode(qrCodeService.createSignedQrPayload(ticket.getTicketIdentifier()));
            ticket.setOrder(order);
            ticket.setTicketType(type);
            ticket.setEvent(order.getEvent());
            ticket.setAttendee(order.getAttendee());
            tickets.add(ticket);
        }
        return ticketRepository.saveAll(tickets);
    }

    private void validateSalesWindow(TicketType type) {
        LocalDateTime now = LocalDateTime.now();
        if (type.getSalesStartTime() != null && now.isBefore(type.getSalesStartTime())
                || type.getSalesEndTime() != null && now.isAfter(type.getSalesEndTime())) {
            throw new BusinessRuleViolationException("Ticket sales are not open");
        }
    }

    private OrderResponse toResponse(Order order, List<Ticket> tickets, LocalDateTime reservationExpiresAt) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setEventId(order.getEvent().getId());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setReservationExpiresAt(reservationExpiresAt);
        response.setTickets(tickets.stream().map(ticket -> {
            TicketResponse t = new TicketResponse();
            t.setId(ticket.getId());
            t.setTicketIdentifier(ticket.getTicketIdentifier());
            t.setEventId(ticket.getEvent().getId());
            t.setTicketTypeId(ticket.getTicketType().getId());
            t.setStatus(ticket.getStatus());
            return t;
        }).toList());
        return response;
    }
}
