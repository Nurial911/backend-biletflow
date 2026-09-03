package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserResponse registerUser(RegisterUserRequest request);

    UserResponse getUserById(Long id);

    UserResponse updateUserCredentials(Long id, UpdateUserRequest request);

    void deleteUser(Long id);

    Page<UserResponse> getAllUsers(Pageable pageable);
}
