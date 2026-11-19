package kz.edu.biletflow.backend.entities;

public enum OrderStatus {
    PENDING,    // inventory reserved until expiresAt, waiting for payment
    PAID,       // tickets issued
    CANCELLED,  // cancelled by the buyer before payment, or a free registration cancelled by the organizer
    EXPIRED,    // not paid in time, inventory released
    REFUNDED    // paid, then refunded in full; its tickets are invalid and back on sale
}
