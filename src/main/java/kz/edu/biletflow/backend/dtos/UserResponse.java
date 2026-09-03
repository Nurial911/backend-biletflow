package kz.edu.biletflow.backend.dtos;

import java.time.LocalDateTime;

public class UserResponse {
    private Long id;
    private String email;
    private String role;
    private boolean verificationStatus;
    private LocalDateTime createdAt;
}
