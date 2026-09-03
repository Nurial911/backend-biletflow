package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterUserRequest {
    @Email
    @NotBlank
    private String email;

    @Size(min = 6, max = 72)
    @NotBlank
    private String password;
}
