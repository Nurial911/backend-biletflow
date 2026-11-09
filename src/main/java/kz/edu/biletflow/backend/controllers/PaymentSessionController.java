package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PaymentSessionResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public interface PaymentSessionController {
    // Start paying: tells the frontend whether to open the bank's page or use the simulator.
    @PostMapping("/api/orders/{orderId}/payment-session")
    ResponseEntity<PaymentSessionResponse> openSession(@AuthenticationPrincipal UserPrincipal currentUser,
                                                       @PathVariable Long orderId);

    // For the page the shopper returns to: asks the bank now, then returns the order.
    @PostMapping("/api/orders/{orderId}/payment-check")
    ResponseEntity<OrderResponse> checkPayment(@AuthenticationPrincipal UserPrincipal currentUser,
                                               @PathVariable Long orderId);

    // Called by the bank, so public (SecurityConfig). A String so an unexpected content type isn't refused.
    @PostMapping("/api/payments/halyk/callback")
    ResponseEntity<Void> halykCallback(@RequestBody(required = false) String body);
}
