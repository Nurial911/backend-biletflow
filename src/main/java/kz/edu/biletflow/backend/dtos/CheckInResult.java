package kz.edu.biletflow.backend.dtos;

// Outcome of a scan, shown by the verification app. Only CHECKED_IN admits the attendee.
public enum CheckInResult {
    CHECKED_IN,     // ticket was valid and is now marked used
    ALREADY_USED,   // ticket was checked in before
    CANCELLED,      // ticket or event is cancelled
    WRONG_EVENT,    // a real ticket, but for another event
    INVALID         // unknown code (forged, mistyped, or not a ticket QR at all)
}
