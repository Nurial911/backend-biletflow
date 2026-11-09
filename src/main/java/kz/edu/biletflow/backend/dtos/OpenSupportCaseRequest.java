package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.edu.biletflow.backend.entities.SupportCaseCategory;
import kz.edu.biletflow.backend.entities.SupportCaseType;
import lombok.Data;

@Data
public class OpenSupportCaseRequest {
    // Optional, defaults to ATTENDEE. PLATFORM is for organizers contacting platform support.
    private SupportCaseType type;

    @NotNull(message = "Category is required")
    private SupportCaseCategory category;

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject must be at most 200 characters long")
    private String subject;

    @NotBlank(message = "Message is required")
    @Size(max = 4000, message = "Message must be at most 4000 characters long")
    private String message;

    // ATTENDEE cases need at least one of these; the server fills in the rest of the context.
    private Long eventId;
    private Long orderId;
    private Long ticketId;
}
