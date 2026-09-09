package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Positive;
import kz.edu.biletflow.backend.entities.VisibilityStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateEventRequest{
    private String title;

    private String description;

    private String category;

    private LocalDateTime startTime;

    private LocalDateTime registrationOpeningTime;
    private LocalDateTime registrationClosingTime;

    @Positive(message = "Capacity must be greater than zero")
    private Integer capacity;

    private VisibilityStatus visibilityStatus;

    private Long venueId;
}
