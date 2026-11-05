package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserResponse getUserById(Long id);

    UserResponse updateUserCredentials(Long id, UpdateUserRequest request);

    UserResponse changeRole(Long adminId, Long userId, User.Role role);

    void deleteUser(Long id);

    Page<UserResponse> getAllUsers(Pageable pageable);
}
