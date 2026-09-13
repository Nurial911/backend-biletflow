package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.dtos.FreeRegistrationRequest;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.TicketResponse;
import kz.edu.biletflow.backend.entities.*;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.repositories.*;
import kz.edu.biletflow.backend.services.RegistrationService;
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
public class RegistrationServiceImpl implements RegistrationService {
    private final UserRepository userRepository;
    private final AttendeeRepository attendeeRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;

    @Override
    @Transactional
    public OrderResponse registerFree(Long userId, Long eventId, FreeRegistrationRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("Tickets can only be registered for published events");
        }
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(request.getTicketTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket type not found with id: " + request.getTicketTypeId()));
        if (!ticketType.getEvent().getId().equals(eventId)) {
            throw new BusinessRuleViolationException("Ticket type does not belong to this event");
        }
        if (ticketType.getPrice().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleViolationException("This ticket type requires checkout payment");
        }
        LocalDateTime now = LocalDateTime.now();
        if (ticketType.getSalesStartTime() != null && now.isBefore(ticketType.getSalesStartTime())
                || ticketType.getSalesEndTime() != null && now.isAfter(ticketType.getSalesEndTime())) {
            throw new BusinessRuleViolationException("Ticket sales are not open");
        }
        if (request.getQuantity() > ticketType.getLimitPerOrder()) {
            throw new BusinessRuleViolationException("Requested quantity exceeds the per-order limit");
        }
        long issued = ticketRepository.countByTicketTypeIdAndStatusIn(ticketType.getId(),
                List.of(TicketStatus.VALID, TicketStatus.CHECKED_IN));
        if (issued + request.getQuantity() > ticketType.getQuantity()) {
            throw new BusinessRuleViolationException("Ticket inventory is exhausted");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        Attendee attendee = attendeeRepository.findByUserId(userId).orElseGet(() -> {
            Attendee newAttendee = new Attendee();
            newAttendee.setUser(user);
            return attendeeRepository.save(newAttendee);
        });

        Order order = new Order();
        order.setAttendee(attendee);
        order.setEvent(event);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setProcessingCharges(BigDecimal.ZERO);
        order.setStatus(OrderStatus.PAID);
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setTicketType(ticketType);
        item.setPrice(BigDecimal.ZERO);
        item.setQuantity(request.getQuantity());
        order.getItems().add(item);
        Order savedOrder = orderRepository.save(order);

        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < request.getQuantity(); i++) {
            Ticket ticket = new Ticket();
            ticket.setTicketIdentifier(UUID.randomUUID().toString());
            ticket.setQrCode(qrCodeService.createSignedQrPayload(ticket.getTicketIdentifier()));
            ticket.setOrder(savedOrder);
            ticket.setTicketType(ticketType);
            ticket.setEvent(event);
            ticket.setAttendee(attendee);
            ticket.setStatus(TicketStatus.VALID);
            tickets.add(ticket);
        }
        ticketRepository.saveAll(tickets);
        return toResponse(savedOrder, tickets);
    }

    private OrderResponse toResponse(Order order, List<Ticket> tickets) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setEventId(order.getEvent().getId());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setCreatedAt(order.getCreatedAt());
        response.setTickets(tickets.stream().map(ticket -> {
            TicketResponse ticketResponse = new TicketResponse();
            ticketResponse.setId(ticket.getId());
            ticketResponse.setTicketIdentifier(ticket.getTicketIdentifier());
            ticketResponse.setEventId(ticket.getEvent().getId());
            ticketResponse.setTicketTypeId(ticket.getTicketType().getId());
            ticketResponse.setStatus(ticket.getStatus());
            return ticketResponse;
        }).toList());
        return response;
    }
}
