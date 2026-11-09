package kz.edu.biletflow.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * How to pay for an order. HALYK: load scriptUrl and call halyk.pay(payment).
 * SIMULATED: there is no bank, call POST /api/orders/{id}/pay instead (scriptUrl and payment are null).
 */
@Data
@AllArgsConstructor
public class PaymentSessionResponse {
    private String provider;
    // True in a test environment: show it as a demonstration, not a real transaction.
    private boolean simulated;
    private String scriptUrl;
    private Map<String, Object> payment;
}
