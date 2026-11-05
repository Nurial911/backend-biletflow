package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SuspendEventRequest {
    // Optional; shown to the organizer so they know why the event was taken down.
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
