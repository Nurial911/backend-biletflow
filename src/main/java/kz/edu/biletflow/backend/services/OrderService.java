package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CreateOrderRequest;
import kz.edu.biletflow.backend.dtos.OrderQuoteResponse;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PaymentResponse;
import kz.edu.biletflow.backend.entities.SimulatedPaymentOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {
    OrderResponse createOrder(Long userId, boolean isPlatformAdmin, CreateOrderRequest request);

    // What the order would cost right now; reserves nothing.
    OrderQuoteResponse quoteOrder(Long userId, boolean isPlatformAdmin, CreateOrderRequest request);

    OrderResponse payOrder(Long userId, Long orderId, SimulatedPaymentOutcome outcome);

    OrderResponse cancelOrder(Long userId, Long orderId);

    Page<OrderResponse> getMyOrders(Long userId, Pageable pageable);

    OrderResponse getOrderById(Long orderId, Long requesterId, boolean isPlatformAdmin);

    List<PaymentResponse> getOrderPayments(Long orderId, Long requesterId, boolean isPlatformAdmin);

    // Called by the scheduler. Returns how many orders were expired.
    int expireStaleOrders();
}
