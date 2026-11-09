package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupportMessageRequest {
    @NotBlank(message = "Message is required")
    @Size(max = 4000, message = "Message must be at most 4000 characters long")
    private String body;
}
