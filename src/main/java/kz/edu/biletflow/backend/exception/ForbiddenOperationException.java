package kz.edu.biletflow.backend.exception;

// 403: signed in, but not allowed. ACCESS_DENIED unless a more specific code is given.
public class ForbiddenOperationException extends CodedException {
    public ForbiddenOperationException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public ForbiddenOperationException(ErrorCode code, String message) {
        super(code, message);
    }
}
