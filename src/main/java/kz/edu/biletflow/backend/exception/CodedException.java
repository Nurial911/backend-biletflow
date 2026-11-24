package kz.edu.biletflow.backend.exception;

import lombok.Getter;

// Base of the exceptions whose ErrorCode is chosen where they are thrown; GlobalExceptionHandler
// copies it into the ErrorResponse. Each subclass has a default code for the general case.
@Getter
public abstract class CodedException extends RuntimeException {
    private final ErrorCode code;

    protected CodedException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
