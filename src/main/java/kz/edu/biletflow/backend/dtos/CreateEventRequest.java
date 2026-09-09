package kz.edu.biletflow.backend.dtos;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import kz.edu.biletflow.backend.entities.VisibilityStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateEventRequest {
    @NotBlank(message = "Title cannot be blank")
    private String title;

    private String description;

    @NotBlank(message = "Category cannot be blank")
    private String category;

    @Future(message = "Start time must be in the future")
    @NotNull(message = "Start time cannot be blank")
    private LocalDateTime startTime;

    private LocalDateTime registrationOpeningTime;
    private LocalDateTime registrationClosingTime;

    @Positive(message = "Capacity must be a positive number")
    private Integer capacity;

    @NotNull(message = "Visibility status cannot be blank")
    private VisibilityStatus visibilityStatus;

    @NotNull(message = "Venue ID cannot be blank")
    private Long venueId;
}
