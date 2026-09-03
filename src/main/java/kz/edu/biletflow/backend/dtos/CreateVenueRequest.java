package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateVenueRequest {
    @NotBlank(message = "Venue name cannot be blank")
    private String name;

    @NotBlank(message = "Venue address cannot be blank")
    private String address;
}
