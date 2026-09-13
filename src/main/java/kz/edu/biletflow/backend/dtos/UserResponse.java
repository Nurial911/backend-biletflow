package kz.edu.biletflow.backend.dtos;

import kz.edu.biletflow.backend.entities.VerificationStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResponse {
    private Long id;
    private String email;
    private String role;
    private VerificationStatus verificationStatus;
    private LocalDateTime createdAt;
}
