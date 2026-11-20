package kz.edu.biletflow.backend.services;

import jakarta.annotation.Nullable;
import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserResponse getUserById(Long id);

    UserResponse updateUserCredentials(Long id, UpdateUserRequest request);

    UserResponse changeRole(Long adminId, Long userId, User.Role role);

    void deleteUser(Long actorId, Long id);

    // Admin search: q matches part of the email or name (case-insensitive) or the exact id;
    // role and suspended narrow the result. All filters are optional.
    Page<UserResponse> searchUsers(@Nullable String q, @Nullable User.Role role, @Nullable Boolean suspended,
                                   Pageable pageable);

    UserResponse suspendUser(Long adminId, Long userId, @Nullable String reason);

    UserResponse unsuspendUser(Long adminId, Long userId);
}
