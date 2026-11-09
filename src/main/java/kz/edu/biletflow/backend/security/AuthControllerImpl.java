package kz.edu.biletflow.backend.security;

import jakarta.validation.Valid;
import kz.edu.biletflow.backend.dtos.EmailAddressRequest;
import kz.edu.biletflow.backend.dtos.RegisterUserRequest;
import kz.edu.biletflow.backend.dtos.ResetPasswordRequest;
import kz.edu.biletflow.backend.dtos.VerifyEmailRequest;
import kz.edu.biletflow.backend.services.AccountEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class AuthControllerImpl {
    private final AuthServiceImpl authService;
    private final AccountEmailService accountEmailService;

    @PostMapping("/login")
    public ResponseEntity<JwtAuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    ResponseEntity<JwtAuthResponse> registerUser(@Valid @RequestBody RegisterUserRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerUser(request));
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        accountEmailService.verifyEmail(request.getToken());
        return ResponseEntity.noContent().build();
    }

    // Answers 204 whether or not the address has an account.
    @PostMapping("/resend-verification")
    ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailAddressRequest request) {
        accountEmailService.requestVerification(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    // Answers 204 whether or not the address has an account.
    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailAddressRequest request) {
        accountEmailService.requestPasswordReset(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        accountEmailService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
