package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScanTicketRequest {
    // The value read from the ticket QR code.
    @NotBlank(message = "Ticket code cannot be blank")
    private String code;
}
