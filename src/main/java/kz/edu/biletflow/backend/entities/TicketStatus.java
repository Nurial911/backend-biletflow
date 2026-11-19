package kz.edu.biletflow.backend.entities;

public enum TicketStatus {
    VALID,
    USED,
    CANCELLED,  // the event was cancelled, or a free registration was cancelled by the organizer
    REFUNDED    // its order was refunded (invalid from the moment the refund starts)
}
