package kz.edu.biletflow.backend.entities;

public enum OrderStatus {
    PENDING,    // inventory reserved until expiresAt, waiting for payment
    PAID,       // tickets issued
    CANCELLED,  // cancelled by the buyer before payment
    EXPIRED     // not paid in time, inventory released
}
