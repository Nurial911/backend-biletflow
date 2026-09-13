package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketVerificationRequest {
    @NotBlank
    private String qrPayload;
}
