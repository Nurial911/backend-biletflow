package kz.edu.biletflow.backend.controllers;

import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/users")
public interface UserController {
    @PostMapping
    ResponseEntity<UserResponse> registerUser(RegisterUserRequest request);
}
