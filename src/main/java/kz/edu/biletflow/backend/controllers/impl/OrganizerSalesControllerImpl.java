package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.OrganizerSalesController;
import kz.edu.biletflow.backend.dtos.EventOrderResponse;
import kz.edu.biletflow.backend.dtos.EventSalesResponse;
import kz.edu.biletflow.backend.dtos.SalesSummaryResponse;
import kz.edu.biletflow.backend.entities.OrderStatus;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrganizerSalesControllerImpl implements OrganizerSalesController {
    private final SalesService salesService;

    @Override
    public ResponseEntity<SalesSummaryResponse> getSalesSummary(UserPrincipal currentUser, List<Long> eventIds) {
        return ResponseEntity.ok(salesService.getSalesSummary(currentUser.getId(), eventIds));
    }

    @Override
    public ResponseEntity<EventSalesResponse> getEventSales(UserPrincipal currentUser, Long eventId) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(salesService.getEventSales(eventId, currentUser.getId(), isPlatformAdmin));
    }

    @Override
    public ResponseEntity<Page<EventOrderResponse>> getEventOrders(UserPrincipal currentUser, Long eventId,
                                                                   OrderStatus status, String q, Pageable pageable) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(salesService.getEventOrders(eventId, currentUser.getId(), isPlatformAdmin,
                status, q, pageable));
    }
}
