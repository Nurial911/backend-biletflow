package kz.edu.biletflow.backend.exception;

// 409 (handled as a DuplicateResourceException): someone else got the seat first, so the client refreshes the map.
public class SeatUnavailableException extends DuplicateResourceException {
    public SeatUnavailableException(String message) {
        super(message);
    }
}
