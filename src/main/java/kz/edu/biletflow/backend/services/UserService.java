package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;

public interface UserService {
    UserResponse registerUser(RegisterUserRequest request);

    UserResponse getUserById(Long id);
}
