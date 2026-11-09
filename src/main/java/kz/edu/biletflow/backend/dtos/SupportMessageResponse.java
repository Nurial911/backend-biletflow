package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SupportMessageResponse {
    private Long id;
    private String authorName;
    private boolean fromStaff;
    private String body;
    private LocalDateTime createdAt;
}
