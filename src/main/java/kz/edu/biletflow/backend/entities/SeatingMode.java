package kz.edu.biletflow.backend.entities;

// How an event sells admission (SRS 4.3.1).
public enum SeatingMode {
    // Ticket types with a quantity, no seat map. Also the right choice for a venue without seats, such as a field.
    GENERAL_ADMISSION,
    // The venue's seat map: seated sections are sold seat by seat, standing sections by count.
    ASSIGNED
}
