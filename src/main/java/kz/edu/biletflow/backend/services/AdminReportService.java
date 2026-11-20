package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;

import java.time.LocalDate;

// Basic operational reports for platform admins, as CSV (SRS 4.12 "export basic operational reports").
// from/to are inclusive dates; both default to the last 30 days, and a range may span at most 366 days.
public interface AdminReportService {
    record CsvReport(String fileName, byte[] content) {
    }

    // One row per event starting in the range: status, orders, tickets and money (as in the organizer sales API).
    CsvReport eventsReport(@Nullable LocalDate from, @Nullable LocalDate to);

    // One row per payment attempt in the range, failed ones included.
    CsvReport paymentsReport(@Nullable LocalDate from, @Nullable LocalDate to);
}
