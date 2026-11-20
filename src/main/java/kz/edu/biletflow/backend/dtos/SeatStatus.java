package kz.edu.biletflow.backend.dtos;

// A seat as the viewer of the seat map sees it (SRS 4.3.1). "Selected" lives in the browser until checkout.
public enum SeatStatus {
    AVAILABLE,
    // In someone else's unpaid order; it comes back if that order expires or is cancelled.
    HELD,
    // In the viewer's own unpaid order.
    YOURS,
    SOLD,
    // Blocked by the organizer, or its section isn't on sale.
    UNAVAILABLE
}
