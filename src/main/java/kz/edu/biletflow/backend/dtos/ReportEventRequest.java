package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.edu.biletflow.backend.entities.EventReport;
import lombok.Data;

@Data
public class ReportEventRequest {
    @NotNull(message = "Reason is required")
    private EventReport.Reason reason;

    @Size(max = 1000, message = "Message must be at most 1000 characters")
    private String message;
}
