package kz.edu.biletflow.backend.exception;

// Payment attempt was refused. The transaction still commits, so the FAILED payment is kept.
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) {
        super(message);
    }
}
