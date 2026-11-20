package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.ChangeRoleRequest;
import kz.edu.biletflow.backend.dtos.SuspendUserRequest;
import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import kz.edu.biletflow.backend.entities.User;
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

    // The user's current token keeps the old role until it expires; they must log in again.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/{id}/role")
    ResponseEntity<UserResponse> changeUserRole(@AuthenticationPrincipal UserPrincipal currentUser,
                                                @PathVariable Long id,
                                                @Valid @RequestBody ChangeRoleRequest request);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteUser(@AuthenticationPrincipal UserPrincipal currentUser, @PathVariable Long id);

    // Search: ?q= part of the email or name (case-insensitive) or the exact id, ?role=, ?suspended=true|false.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @GetMapping
    ResponseEntity<Page<UserResponse>> searchUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) Boolean suspended,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);

    // Blocks sign-in and every token already issued; unpaid orders are cancelled, bought tickets stay valid.
    // Not on yourself or on a platform admin.
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/{id}/suspend")
    ResponseEntity<UserResponse> suspendUser(@AuthenticationPrincipal UserPrincipal currentUser,
                                             @PathVariable Long id,
                                             @Valid @RequestBody(required = false) SuspendUserRequest request);

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping("/{id}/unsuspend")
    ResponseEntity<UserResponse> unsuspendUser(@AuthenticationPrincipal UserPrincipal currentUser,
                                               @PathVariable Long id);

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
