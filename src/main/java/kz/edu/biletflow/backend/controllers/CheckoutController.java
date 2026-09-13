package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.CheckoutRequest;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PaymentRequest;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CheckoutController {
    private final CheckoutService checkoutService;

    @PostMapping("/events/{eventId}/checkout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal UserPrincipal user,
                                                  @PathVariable Long eventId,
                                                  @Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(checkoutService.createCheckout(user.getId(), eventId, request));
    }

    @PostMapping("/orders/{orderId}/pay")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> pay(@AuthenticationPrincipal UserPrincipal user,
                                              @PathVariable Long orderId,
                                              @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(checkoutService.pay(user.getId(), orderId, request));
    }

    @PostMapping("/orders/{orderId}/refund")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrderResponse> refund(@AuthenticationPrincipal UserPrincipal user,
                                                 @PathVariable Long orderId) {
        return ResponseEntity.ok(checkoutService.refund(user.getId(), orderId));
    }
}
