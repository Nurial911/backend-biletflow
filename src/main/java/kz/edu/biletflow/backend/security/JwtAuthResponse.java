package kz.edu.biletflow.backend.security;

import kz.edu.biletflow.backend.dtos.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtAuthResponse {
    private String accessToken;
    private UserResponse user;
}