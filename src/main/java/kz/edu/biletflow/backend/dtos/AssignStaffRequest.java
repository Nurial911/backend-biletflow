package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignStaffRequest {
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email must be valid")
    private String email;
}
