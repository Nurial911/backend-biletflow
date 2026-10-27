package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CreateOrderRequest;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderResponse createOrder(Long userId, boolean isPlatformAdmin, CreateOrderRequest request);

    OrderResponse payOrder(Long userId, Long orderId);

    OrderResponse cancelOrder(Long userId, Long orderId);

    Page<OrderResponse> getMyOrders(Long userId, Pageable pageable);

    OrderResponse getOrderById(Long orderId, Long requesterId, boolean isPlatformAdmin);

    // Called by the scheduler. Returns how many orders were expired.
    int expireStaleOrders();
}
