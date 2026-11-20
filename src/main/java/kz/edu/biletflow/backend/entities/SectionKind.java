package kz.edu.biletflow.backend.entities;

public enum SectionKind {
    // Rows and numbered seats; each seat is sold once.
    SEATED,
    // An area without seats (a stadium field, a dance floor): only a capacity, sold by count.
    STANDING
}
