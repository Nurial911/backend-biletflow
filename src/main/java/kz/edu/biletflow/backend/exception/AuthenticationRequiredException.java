package kz.edu.biletflow.backend.exception;

// 401 UNAUTHENTICATED: a protected endpoint was called without a valid token (none, malformed or expired).
// Raised by Spring Security's entry point (SecurityConfig), so the body has the usual ErrorResponse shape.
public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException() {
        super("Authentication required: sign in and send the access token");
    }
}
