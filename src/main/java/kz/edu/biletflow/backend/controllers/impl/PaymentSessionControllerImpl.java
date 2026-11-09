package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.PaymentSessionController;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PaymentSessionResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.PaymentSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentSessionControllerImpl implements PaymentSessionController {
    private final PaymentSessionService paymentSessionService;

    @Override
    public ResponseEntity<PaymentSessionResponse> openSession(UserPrincipal currentUser, Long orderId) {
        return ResponseEntity.ok(paymentSessionService.openSession(currentUser.getId(), orderId));
    }

    @Override
    public ResponseEntity<OrderResponse> checkPayment(UserPrincipal currentUser, Long orderId) {
        boolean isPlatformAdmin = currentUser.hasRole("PLATFORM_ADMIN");
        return ResponseEntity.ok(paymentSessionService.checkPayment(orderId, currentUser.getId(), isPlatformAdmin));
    }

    @Override
    public ResponseEntity<Void> halykCallback(String body) {
        paymentSessionService.handleHalykCallback(body);
        return ResponseEntity.ok().build();
    }
}
