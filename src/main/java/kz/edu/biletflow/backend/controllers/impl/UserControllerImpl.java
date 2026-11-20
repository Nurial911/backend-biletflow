package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.UserController;
import kz.edu.biletflow.backend.dtos.ChangeRoleRequest;
import kz.edu.biletflow.backend.dtos.SuspendUserRequest;
import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.security.UserPrincipal;
import kz.edu.biletflow.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements UserController {
    private final UserService userService;

    @Override
    public ResponseEntity<UserResponse> getUserById(Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Override
    public ResponseEntity<UserResponse> updateUserCredentials(Long id, UpdateUserRequest request) {
        var updatedUser = userService.updateUserCredentials(id, request);
        return ResponseEntity.ok(updatedUser);
    }

    @Override
    public ResponseEntity<UserResponse> changeUserRole(UserPrincipal currentUser, Long id, ChangeRoleRequest request) {
        return ResponseEntity.ok(userService.changeRole(currentUser.getId(), id, request.getRole()));
    }

    @Override
    public ResponseEntity<Void> deleteUser(UserPrincipal currentUser, Long id) {
        userService.deleteUser(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Page<UserResponse>> searchUsers(String q, User.Role role, Boolean suspended,
                                                          Pageable pageable) {
        return ResponseEntity.ok(userService.searchUsers(q, role, suspended, pageable));
    }

    @Override
    public ResponseEntity<UserResponse> suspendUser(UserPrincipal currentUser, Long id, SuspendUserRequest request) {
        String reason = request != null ? request.getReason() : null;
        return ResponseEntity.ok(userService.suspendUser(currentUser.getId(), id, reason));
    }

    @Override
    public ResponseEntity<UserResponse> unsuspendUser(UserPrincipal currentUser, Long id) {
        return ResponseEntity.ok(userService.unsuspendUser(currentUser.getId(), id));
    }

    @Override
    public ResponseEntity<UserResponse> getCurrentUser(UserPrincipal currentUser) {
        return ResponseEntity.ok(userService.getUserById(currentUser.getId()));
    }

    @Override
    public ResponseEntity<UserResponse> updateOwnCredentials(UserPrincipal currentUser, UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUserCredentials(currentUser.getId(), request));
    }

    @Override
    public ResponseEntity<Void> deleteOwnAccount(UserPrincipal currentUser) {
        userService.deleteUser(currentUser.getId(), currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}
