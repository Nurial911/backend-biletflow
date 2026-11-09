package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailAddressRequest {
    @Email(message = "Invalid email format")
    @NotBlank
    private String email;
}
