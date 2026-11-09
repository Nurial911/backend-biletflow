package kz.edu.biletflow.backend.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String role;
    private boolean verificationStatus;
    private LocalDateTime createdAt;
}
