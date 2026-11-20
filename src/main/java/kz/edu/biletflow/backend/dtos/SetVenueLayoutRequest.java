package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetVenueLayoutRequest {
    // Key from GET /api/venues/seating-layouts; null removes the seat map (general admission only).
    @Size(max = 50, message = "Layout key must be at most 50 characters long")
    private String seatingLayout;
}
