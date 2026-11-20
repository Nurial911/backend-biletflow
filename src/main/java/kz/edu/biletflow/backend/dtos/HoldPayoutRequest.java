package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// The reason is sent to the organizer and shown on the payout.
@Data
public class HoldPayoutRequest {
    @NotBlank(message = "A reason is required")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
