package kz.edu.biletflow.backend.controllers.impl;

import kz.edu.biletflow.backend.controllers.UserController;
import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements UserController {
    private final UserService userService;

    @Override
    public ResponseEntity<UserResponse> registerUser(RegisterUserRequest request) {
        UserResponse createdUser = userService.registerUser(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/users/{id}")
                .buildAndExpand(createdUser.getId())
                .toUri();
        return ResponseEntity.created(location).body(createdUser);
    }

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
    public ResponseEntity<Void> deleteUser(Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Page<UserResponse>> getAllUsers(Pageable pageable) {
        Page<UserResponse> users = userService.getAllUsers(pageable);
        return ResponseEntity.ok(users);
    }
}
