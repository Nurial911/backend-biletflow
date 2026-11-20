package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.edu.biletflow.backend.entities.EventReport;
import lombok.Data;

@Data
public class ReviewEventReportRequest {
    // DISMISSED or ACTIONED. Suspending the event is a separate call (POST /api/events/{id}/suspend).
    @NotNull(message = "Outcome is required")
    private EventReport.Status outcome;

    // For other admins only; the reporter isn't shown it.
    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;
}
