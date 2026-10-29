package kz.edu.biletflow.backend.security;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Email cannot be blank")
    @Email
    private String email;

    // Browsers/mobile keyboards often add a trailing space or capitalise the first letter.
    // Trim here so @Email validation sees the clean value; case is ignored in the lookup.
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    @NotBlank(message = "Password cannot be blank")
    private String password;
}
