package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.EventOrderResponse;
import kz.edu.biletflow.backend.dtos.EventSalesResponse;
import kz.edu.biletflow.backend.dtos.SalesSummaryResponse;
import kz.edu.biletflow.backend.entities.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SalesService {
    SalesSummaryResponse getSalesSummary(Long organizerId, @Nullable List<Long> eventIds);

    EventSalesResponse getEventSales(Long eventId, Long requesterId, boolean isPlatformAdmin);

    Page<EventOrderResponse> getEventOrders(Long eventId, Long requesterId, boolean isPlatformAdmin,
                                            @Nullable OrderStatus status, @Nullable String q, Pageable pageable);
}
