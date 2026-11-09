package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank
    private String token;

    @Size(min = 6, message = "Password must be at least 6 characters long")
    @NotBlank
    private String newPassword;
}
