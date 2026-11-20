package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateVenueRequest {
    @NotBlank(message = "Venue name cannot be blank")
    private String name;

    @NotBlank(message = "Venue address cannot be blank")
    private String address;

    // Required unless a seating layout is given; a venue with a seat map holds what the map holds.
    @Positive(message = "Venue capacity must be a positive number")
    private Integer capacity;

    // Optional key of a predefined seat map (GET /api/venues/seating-layouts). Leave it out for a venue without
    // seats, such as a field: its events use general admission.
    @Size(max = 50, message = "Layout key must be at most 50 characters long")
    private String seatingLayout;
}
