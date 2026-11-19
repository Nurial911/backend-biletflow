package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CreateOrderRequest;
import kz.edu.biletflow.backend.dtos.OrderQuoteResponse;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PayOrderRequest;
import kz.edu.biletflow.backend.dtos.PaymentResponse;
import kz.edu.biletflow.backend.dtos.RefundOrderRequest;
import kz.edu.biletflow.backend.dtos.RefundResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Any authenticated user may buy tickets (SecurityConfig: anyRequest().authenticated()).
@RequestMapping("/api/orders")
public interface OrderController {
    @PostMapping
    ResponseEntity<OrderResponse> createOrder(@AuthenticationPrincipal UserPrincipal currentUser,
                                              @Valid @RequestBody CreateOrderRequest request);

    // Same body as creating the order: shows the discount and total before checkout, reserves nothing.
    @PostMapping("/quote")
    ResponseEntity<OrderQuoteResponse> quoteOrder(@AuthenticationPrincipal UserPrincipal currentUser,
                                                  @Valid @RequestBody CreateOrderRequest request);

    @GetMapping
    ResponseEntity<Page<OrderResponse>> getMyOrders(@AuthenticationPrincipal UserPrincipal currentUser,
                                                    Pageable pageable);

    @GetMapping("/{orderId}")
    ResponseEntity<OrderResponse> getOrder(@AuthenticationPrincipal UserPrincipal currentUser,
                                           @PathVariable Long orderId);

    // Owner or platform admin; anyone else gets 404.
    @GetMapping("/{orderId}/payments")
    ResponseEntity<List<PaymentResponse>> getOrderPayments(@AuthenticationPrincipal UserPrincipal currentUser,
                                                           @PathVariable Long orderId);

    // Simulated payment. Optional body {"simulatedOutcome": "DECLINED"} shows the failure path (402).
    @PostMapping("/{orderId}/pay")
    ResponseEntity<OrderResponse> payOrder(@AuthenticationPrincipal UserPrincipal currentUser,
                                           @PathVariable Long orderId,
                                           @RequestBody(required = false) PayOrderRequest request);

    @PostMapping("/{orderId}/cancel")
    ResponseEntity<OrderResponse> cancelOrder(@AuthenticationPrincipal UserPrincipal currentUser,
                                              @PathVariable Long orderId);

    // Organizer of the order's event or platform admin (other organizers get 404). Refunds a paid order in full,
    // or cancels a free registration. Optional body {"reason": "..."} is shown to the buyer.
    @PreAuthorize("hasAnyRole('ORGANIZER', 'PLATFORM_ADMIN')")
    @PostMapping("/{orderId}/refund")
    ResponseEntity<OrderResponse> refundOrder(@AuthenticationPrincipal UserPrincipal currentUser,
                                              @PathVariable Long orderId,
                                              @Valid @RequestBody(required = false) RefundOrderRequest request);

    // Buyer, organizer of the event or platform admin; anyone else gets 404.
    @GetMapping("/{orderId}/refunds")
    ResponseEntity<List<RefundResponse>> getOrderRefunds(@AuthenticationPrincipal UserPrincipal currentUser,
                                                         @PathVariable Long orderId);
}
