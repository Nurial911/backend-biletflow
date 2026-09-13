package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.CheckoutRequest;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PaymentRequest;

public interface CheckoutService {
    OrderResponse createCheckout(Long userId, Long eventId, CheckoutRequest request);
    OrderResponse pay(Long userId, Long orderId, PaymentRequest request);
    OrderResponse refund(Long userId, Long orderId);
}
