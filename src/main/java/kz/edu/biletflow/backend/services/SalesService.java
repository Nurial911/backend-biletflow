package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.EventOrderResponse;
import kz.edu.biletflow.backend.dtos.EventSalesResponse;
import kz.edu.biletflow.backend.dtos.SalesSummaryResponse;
import kz.edu.biletflow.backend.entities.Event;
import kz.edu.biletflow.backend.entities.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface SalesService {
    SalesSummaryResponse getSalesSummary(Long organizerId, @Nullable List<Long> eventIds);

    // Totals of the given events, in the same order, without salesByDay. No access check: for admin reports only.
    List<EventSalesResponse> getEventsSales(List<Event> events);

    // from/to (inclusive dates) and ticketTypeId filter only salesByDay; the totals cover the whole event.
    EventSalesResponse getEventSales(Long eventId, Long requesterId, boolean isPlatformAdmin,
                                     @Nullable LocalDate from, @Nullable LocalDate to, @Nullable Long ticketTypeId);

    Page<EventOrderResponse> getEventOrders(Long eventId, Long requesterId, boolean isPlatformAdmin,
                                            @Nullable OrderStatus status, @Nullable String q, Pageable pageable);
}
