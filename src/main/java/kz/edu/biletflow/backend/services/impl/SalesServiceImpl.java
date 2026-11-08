package kz.edu.biletflow.backend.services.impl;

import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import kz.edu.biletflow.backend.dtos.DailySalesResponse;
import kz.edu.biletflow.backend.dtos.EventOrderResponse;
import kz.edu.biletflow.backend.dtos.EventSalesResponse;
import kz.edu.biletflow.backend.dtos.SalesSummaryResponse;
import kz.edu.biletflow.backend.dtos.TicketTypeSalesResponse;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.Order;
import kz.edu.biletflow.backend.entities.OrderItem;
import kz.edu.biletflow.backend.entities.OrderStatus;
import kz.edu.biletflow.backend.entities.PaymentStatus;
import kz.edu.biletflow.backend.entities.TicketType;
import kz.edu.biletflow.backend.exception.ResourceNotFoundException;
import kz.edu.biletflow.backend.mappers.OrderMapper;
import kz.edu.biletflow.backend.repositories.EventRepository;
import kz.edu.biletflow.backend.repositories.OrderItemRepository;
import kz.edu.biletflow.backend.repositories.OrderRepository;
import kz.edu.biletflow.backend.repositories.PaymentRepository;
import kz.edu.biletflow.backend.repositories.TicketTypeRepository;
import kz.edu.biletflow.backend.services.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Organizer sales figures. Everything is aggregated in the database (a few grouped queries per
 * request, independent of the number of orders). Counting rules are documented on EventSalesResponse.
 */
@Service
@RequiredArgsConstructor
public class SalesServiceImpl implements SalesService {
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final OrderMapper orderMapper;

    @Override
    @Transactional(readOnly = true)
    public SalesSummaryResponse getSalesSummary(Long organizerId, @Nullable List<Long> eventIds) {
        List<Event> events;
        if (eventIds == null || eventIds.isEmpty()) {
            events = eventRepository.findAllByOrganizerIdOrderByStartTimeDesc(organizerId);
        } else {
            events = new ArrayList<>();
            for (Long id : new LinkedHashSet<>(eventIds)) {
                events.add(getManagedEvent(id, organizerId, false)); // someone else's id → 404, never data
            }
            events.sort(Comparator.comparing(Event::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
        }

        List<EventSalesResponse> eventSales = buildEventSales(events);

        SalesSummaryResponse.Totals totals = new SalesSummaryResponse.Totals();
        totals.setEvents(eventSales.size());
        totals.setRevenue(money(BigDecimal.ZERO));
        totals.setProcessingFees(money(BigDecimal.ZERO));
        totals.setEstimatedPayout(money(BigDecimal.ZERO));
        for (EventSalesResponse e : eventSales) {
            totals.setProcessingFees(totals.getProcessingFees().add(e.getProcessingFees()));
            totals.setEstimatedPayout(totals.getEstimatedPayout().add(e.getEstimatedPayout()));
            totals.setPaidOrders(totals.getPaidOrders() + e.getPaidOrders());
            totals.setPendingOrders(totals.getPendingOrders() + e.getPendingOrders());
            totals.setTicketsSold(totals.getTicketsSold() + e.getTicketsSold());
            totals.setTicketsReserved(totals.getTicketsReserved() + e.getTicketsReserved());
            totals.setRevenue(totals.getRevenue().add(e.getRevenue()));
        }

        SalesSummaryResponse response = new SalesSummaryResponse();
        response.setEvents(eventSales);
        response.setTotals(totals);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public EventSalesResponse getEventSales(Long eventId, Long requesterId, boolean isPlatformAdmin) {
        Event event = getManagedEvent(eventId, requesterId, isPlatformAdmin);
        EventSalesResponse response = buildEventSales(List.of(event)).getFirst();

        List<DailySalesResponse> daily = orderItemRepository.dailyPaidSales(eventId, OrderStatus.PAID).stream()
                .map(row -> new DailySalesResponse((LocalDate) row[0], toLong(row[1]), toLong(row[2]), toMoney(row[3])))
                .toList();
        response.setSalesByDay(daily);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventOrderResponse> getEventOrders(Long eventId, Long requesterId, boolean isPlatformAdmin,
                                                   @Nullable OrderStatus status, @Nullable String q, Pageable pageable) {
        getManagedEvent(eventId, requesterId, isPlatformAdmin);

        String emailPattern = (q == null || q.isBlank()) ? null
                : "%" + q.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";

        // The event condition is always part of the query, so the result can only contain this event's orders.
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("event").get("id"), eventId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (emailPattern != null) {
                var user = root.join("user", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(user.get("email")), emailPattern, '\\'));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Order> page = orderRepository.findAll(spec, pageable);
        if (page.isEmpty()) {
            return page.map(orderMapper::toEventOrderDto);
        }

        Map<Long, Object[]> buyers = orderRepository.findBuyers(page.map(Order::getId).getContent()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        return page.map(order -> {
            EventOrderResponse dto = orderMapper.toEventOrderDto(order);
            Object[] buyer = buyers.get(order.getId());
            if (buyer != null) {
                dto.setBuyerId((Long) buyer[1]);
                dto.setBuyerEmail((String) buyer[2]);
            }
            dto.setTicketCount(order.getItems().stream().mapToInt(OrderItem::getQuantity).sum());
            return dto;
        });
    }

    // helpers //

    private List<EventSalesResponse> buildEventSales(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = events.stream().map(Event::getId).toList();
        LocalDateTime now = LocalDateTime.now();

        Map<Long, List<TicketType>> typesByEvent = ticketTypeRepository.findAllByEventIdInOrderByIdAsc(eventIds).stream()
                .collect(Collectors.groupingBy(t -> t.getEvent().getId()));

        Map<Long, Object[]> itemStats = orderItemRepository
                .aggregateSalesByTicketType(eventIds, OrderStatus.PAID, OrderStatus.PENDING, now).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        Map<Long, Object[]> orderStats = orderRepository
                .countOrdersByEvent(eventIds, OrderStatus.PAID, OrderStatus.PENDING,
                        OrderStatus.CANCELLED, OrderStatus.EXPIRED, now).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> row));

        Map<Long, BigDecimal> feesByEvent = paymentRepository
                .sumProcessingFeesByEvent(eventIds, PaymentStatus.SUCCEEDED).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> toMoney(row[1])));

        List<EventSalesResponse> result = new ArrayList<>();
        for (Event event : events) {
            EventSalesResponse dto = new EventSalesResponse();
            dto.setEventId(event.getId());
            dto.setEventTitle(event.getTitle());
            dto.setStatus(event.getStatus());
            dto.setSuspended(event.isSuspended());
            dto.setStartTime(event.getStartTime());
            dto.setEndTime(event.getEndTime());
            dto.setCapacity(event.getCapacity());

            Object[] counts = orderStats.get(event.getId());
            if (counts != null) {
                dto.setPaidOrders(toLong(counts[1]));
                dto.setPendingOrders(toLong(counts[2]));
                dto.setCancelledOrders(toLong(counts[3]));
                dto.setExpiredOrders(toLong(counts[4]));
            }

            long sold = 0;
            long reserved = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            List<TicketTypeSalesResponse> types = new ArrayList<>();
            for (TicketType type : typesByEvent.getOrDefault(event.getId(), List.of())) {
                Object[] stats = itemStats.get(type.getId());
                long typeSold = stats != null ? toLong(stats[1]) : 0;
                long typeReserved = stats != null ? toLong(stats[2]) : 0;
                BigDecimal typeRevenue = stats != null ? toMoney(stats[3]) : money(BigDecimal.ZERO);

                TicketTypeSalesResponse t = new TicketTypeSalesResponse();
                t.setTicketTypeId(type.getId());
                t.setName(type.getName());
                t.setPrice(type.getPrice());
                t.setHidden(type.isHidden());
                t.setQuantity(type.getQuantity());
                t.setSold(typeSold);
                t.setReserved(typeReserved);
                t.setAvailable(Math.max(type.getQuantity() - typeSold - typeReserved, 0));
                t.setRevenue(typeRevenue);
                types.add(t);

                sold += typeSold;
                reserved += typeReserved;
                revenue = revenue.add(typeRevenue);
            }
            dto.setTicketTypes(types);
            dto.setTicketsSold(sold);
            dto.setTicketsReserved(reserved);
            dto.setRevenue(money(revenue));
            BigDecimal fees = feesByEvent.getOrDefault(event.getId(), money(BigDecimal.ZERO));
            dto.setProcessingFees(fees);
            dto.setEstimatedPayout(money(revenue).subtract(fees));
            result.add(dto);
        }
        return result;
    }

    // Organizer of the event or a platform admin; anyone else gets 404 (don't reveal the event exists).
    private Event getManagedEvent(Long eventId, Long requesterId, boolean isPlatformAdmin) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found with id: " + eventId));
        if (!isPlatformAdmin && !event.getOrganizer().getId().equals(requesterId)) {
            throw new ResourceNotFoundException("Event not found with id: " + eventId);
        }
        return event;
    }

    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static BigDecimal toMoney(Object value) {
        if (value == null) {
            return money(BigDecimal.ZERO);
        }
        BigDecimal amount = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
        return money(amount);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
