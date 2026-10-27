package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.OrderController;
import kz.edu.biletflow.backend.dtos.CreateOrderRequest;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class OrderControllerImpl implements OrderController {
    private final OrderService orderService;

    @Override
    public ResponseEntity<OrderResponse> createOrder(UserPrincipal currentUser, CreateOrderRequest request) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        OrderResponse created = orderService.createOrder(currentUser.getId(), isPlatformAdmin, request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/orders/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Override
    public ResponseEntity<Page<OrderResponse>> getMyOrders(UserPrincipal currentUser, Pageable pageable) {
        return ResponseEntity.ok(orderService.getMyOrders(currentUser.getId(), pageable));
    }

    @Override
    public ResponseEntity<OrderResponse> getOrder(UserPrincipal currentUser, Long orderId) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(orderService.getOrderById(orderId, currentUser.getId(), isPlatformAdmin));
    }

    @Override
    public ResponseEntity<OrderResponse> payOrder(UserPrincipal currentUser, Long orderId) {
        return ResponseEntity.ok(orderService.payOrder(currentUser.getId(), orderId));
    }

    @Override
    public ResponseEntity<OrderResponse> cancelOrder(UserPrincipal currentUser, Long orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(currentUser.getId(), orderId));
    }
}
