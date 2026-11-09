package kz.edu.biletflow.backend.services;

import kz.edu.biletflow.backend.entities.User;

// Email verification and password reset links.
public interface AccountEmailService {
    // Sends a new verification link once the surrounding transaction commits.
    void sendVerification(User user);

    // These two stay silent for unknown addresses, so they can't be used to find out who has an account.
    void requestVerification(String email);

    void requestPasswordReset(String email);

    void verifyEmail(String token);

    void resetPassword(String token, String newPassword);
}
