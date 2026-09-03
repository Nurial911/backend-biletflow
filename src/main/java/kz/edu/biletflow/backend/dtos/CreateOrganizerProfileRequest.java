package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrganizerProfileRequest {
    @NotBlank(message = "Contact information cannot be blank")
    private String contactInformation;
}
