package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.time.LocalDateTime;

// What needs a platform admin's attention, for the admin dashboard (SRS 4.12).
@Data
public class AdminOverviewResponse {
    private LocalDateTime generatedAt;

    private long users;
    private long suspendedUsers;
    private long pendingOrganizerVerifications;

    private long publishedEvents;
    private long suspendedEvents;
    private long openEventReports;
    private long suspendedPaidSales;

    private long failedPaymentsLast24Hours;
    private long failedPaymentsLast7Days;
    private long pendingRefunds;
    private long failedRefundsLast7Days;

    private long escalatedSupportCases; // escalated and not resolved
}
