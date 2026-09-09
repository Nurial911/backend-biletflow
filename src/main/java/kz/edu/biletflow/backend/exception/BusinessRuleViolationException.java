package kz.edu.biletflow.backend.exception;

// For domain rules that can't be expressed as simple bean-validation annotations,
// e.g. "registration must close before the event starts".
public class BusinessRuleViolationException extends RuntimeException {
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
