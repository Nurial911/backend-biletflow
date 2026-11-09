package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationResponse {
    private Long id;
    private String type;
    private String title;
    private String body;
    private Long orderId;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
