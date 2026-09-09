package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.VisibilityStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EventResponse {
    private Long id;
    private String title;
    private String description;
    private String category;
    private LocalDateTime startTime;
    private LocalDateTime registrationOpeningTime;
    private LocalDateTime registrationClosingTime;
    private Integer capacity;
    private VisibilityStatus visibilityStatus;

    private Long organizerId;
    private VenueResponse venue;
}
