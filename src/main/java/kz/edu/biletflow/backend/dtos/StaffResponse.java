package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StaffResponse {
    private Long eventId;
    private Long userId;
    private String email;
    private LocalDateTime assignedAt;
}
