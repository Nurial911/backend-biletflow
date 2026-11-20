package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SuspendUserRequest {
    // Optional; included in the email to the user and in the login error they see.
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
