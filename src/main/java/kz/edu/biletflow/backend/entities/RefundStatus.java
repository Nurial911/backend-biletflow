package kz.edu.biletflow.backend.entities;

public enum RefundStatus {
    PENDING,    // the bank was asked and hasn't answered yet; the order's tickets are already invalid
    SUCCEEDED,  // money returned (or, for a simulated payment, recorded as returned)
    FAILED      // the bank refused; the tickets are valid again and the organizer may try again
}
