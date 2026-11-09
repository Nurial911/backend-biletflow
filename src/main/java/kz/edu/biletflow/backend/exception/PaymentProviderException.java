package kz.edu.biletflow.backend.exception;

// The bank could not be reached or refused a request. Nothing about the order has changed.
public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
