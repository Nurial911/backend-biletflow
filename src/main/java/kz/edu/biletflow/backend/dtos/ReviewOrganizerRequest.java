package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewOrganizerRequest {
    @NotNull(message = "approved is required")
    private Boolean approved;

    // Optional; passed on to the organizer.
    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;
}
