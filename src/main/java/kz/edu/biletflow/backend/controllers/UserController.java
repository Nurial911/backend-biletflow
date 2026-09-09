package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/users")
public interface UserController {
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping("/{id}")
    ResponseEntity<UserResponse> getUserById(@PathVariable Long id);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    ResponseEntity<UserResponse> updateUserCredentials(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateUserRequest request);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteUser(@PathVariable Long id);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping
    ResponseEntity<Page<UserResponse>> getAllUsers(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);

    // ==========================================
    // ЗОНА САМООБСЛУЖИВАНИЯ (Только свои данные)
    // ==========================================

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser);

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/me/credentials")
    ResponseEntity<UserResponse> updateOwnCredentials(@AuthenticationPrincipal UserPrincipal currentUser,
                                                      @Valid @RequestBody UpdateUserRequest request);
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteOwnAccount(@AuthenticationPrincipal UserPrincipal currentUser);

}
