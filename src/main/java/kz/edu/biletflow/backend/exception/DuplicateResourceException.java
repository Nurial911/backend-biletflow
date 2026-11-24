package kz.edu.biletflow.backend.exception;

// 409: the thing already exists or was just taken by someone else.
public class DuplicateResourceException extends CodedException {
    public DuplicateResourceException(String message) {
        super(ErrorCode.CONFLICT, message);
    }

    public DuplicateResourceException(ErrorCode code, String message) {
        super(code, message);
    }
}
