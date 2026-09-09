package kz.edu.biletflow.backend.security;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class AuthControllerImpl {
    private final AuthServiceImpl authService;

    @PostMapping("/login")
    public ResponseEntity<JwtAuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping
    ResponseEntity<JwtAuthResponse> registerUser(@Valid @RequestBody RegisterUserRequest request){
        JwtAuthResponse createdUser = authService.registerUser(request);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/users/{id}")
                .buildAndExpand(createdUser.getUser().getId())
                .toUri();
        return ResponseEntity.created(location).body(createdUser);
    };
}
