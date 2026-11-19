package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundOrderRequest {
    // Optional; shown to the buyer in the refund notification.
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
