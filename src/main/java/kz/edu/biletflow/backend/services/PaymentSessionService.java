package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PaymentSessionResponse;

public interface PaymentSessionService {
    PaymentSessionResponse openSession(Long userId, Long orderId);

    // Asks the bank about the order's unfinished payments right now, then returns the order.
    OrderResponse checkPayment(Long orderId, Long requesterId, boolean isPlatformAdmin);

    // The bank's result call. Its content is never trusted: it only prompts a status check.
    void handleHalykCallback(String body);

    // Called by the scheduler. Returns how many sessions were checked.
    int settleOpenSessions();
}
