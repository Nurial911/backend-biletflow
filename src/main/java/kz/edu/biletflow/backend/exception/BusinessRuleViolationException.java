package kz.edu.biletflow.backend.exception;

// For domain rules that can't be expressed as simple bean-validation annotations,
// e.g. "registration must close before the event starts". 400.
public class BusinessRuleViolationException extends CodedException {
    public BusinessRuleViolationException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    // A case clients react to on their own (a closed registration, a used-up promo code, ...).
    public BusinessRuleViolationException(ErrorCode code, String message) {
        super(code, message);
    }
}
