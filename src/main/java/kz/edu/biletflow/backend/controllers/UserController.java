package kz.edu.biletflow.backend.controllers;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UpdateUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/users")
public interface UserController {
    @PostMapping
    ResponseEntity<UserResponse> registerUser(@Valid @RequestBody RegisterUserRequest request);

    @GetMapping("/{id}")
    ResponseEntity<UserResponse> getUserById(@PathVariable Long id);

    @PutMapping("/{id}")
    ResponseEntity<UserResponse> updateUserCredentials(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateUserRequest request);

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteUser(@PathVariable Long id);

    @GetMapping
    ResponseEntity<Page<UserResponse>> getAllUsers(@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable);

}
