package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateVenueRequest {
    @NotBlank(message = "Venue name cannot be blank")
    private String name;

    @NotBlank(message = "Venue address cannot be blank")
    private String address;

    @NotNull(message = "Venue capacity is required")
    @Positive(message = "Venue capacity must be a positive number")
    private Integer capacity;
}
