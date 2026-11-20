package kz.edu.biletflow.backend.mappers;

import jakarta.annotation.Nullable;

// One wording for a seat everywhere: order, ticket, PDF, email and check-in.
public final class SeatLabels {
    private SeatLabels() {
    }

    // "Stalls, row A, seat 5" for a seat, "Field (standing)" for a standing section, null for general admission.
    @Nullable
    public static String describe(@Nullable String sectionName, @Nullable String rowLabel, @Nullable Integer seatNumber) {
        if (sectionName == null) {
            return null;
        }
        if (rowLabel == null || seatNumber == null) {
            return sectionName;
        }
        return sectionName + ", row " + rowLabel + ", seat " + seatNumber;
    }
}
