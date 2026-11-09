package kz.edu.biletflow.backend.services.impl;

import kz.edu.biletflow.backend.entities.AccountToken;
import kz.edu.biletflow.backend.entities.AccountTokenPurpose;
import kz.edu.biletflow.backend.entities.User;
import kz.edu.biletflow.backend.exception.BusinessRuleViolationException;
import kz.edu.biletflow.backend.repositories.AccountTokenRepository;
import kz.edu.biletflow.backend.repositories.UserRepository;
import kz.edu.biletflow.backend.services.AccountEmailService;
import kz.edu.biletflow.backend.services.OutgoingMail;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AccountEmailServiceImpl implements AccountEmailService {
    private static final Duration VERIFICATION_LIFETIME = Duration.ofHours(24);
    private static final Duration RESET_LIFETIME = Duration.ofHours(1);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final String INVALID_LINK = "This link is invalid or has expired";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountTokenRepository accountTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountMailSender accountMailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void sendVerification(User user) {
        String link = issueLink(user, AccountTokenPurpose.EMAIL_VERIFICATION, VERIFICATION_LIFETIME, "/verify-email");
        mailAfterCommit(new OutgoingMail(user.getEmail(), "Confirm your BiletFlow email address",
                "Open this link to confirm your email address:\n\n" + link
                        + "\n\nThe link works once and expires in 24 hours. If you didn't create a BiletFlow account, ignore this email."));
    }

    @Override
    @Transactional
    public void requestVerification(String email) {
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(user -> !user.isVerificationStatus())
                .filter(user -> !sentRecently(user, AccountTokenPurpose.EMAIL_VERIFICATION))
                .ifPresent(this::sendVerification);
    }

    @Override
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(user -> !sentRecently(user, AccountTokenPurpose.PASSWORD_RESET))
                .ifPresent(user -> {
                    String link = issueLink(user, AccountTokenPurpose.PASSWORD_RESET, RESET_LIFETIME, "/reset-password");
                    mailAfterCommit(new OutgoingMail(user.getEmail(), "Reset your BiletFlow password",
                            "Open this link to choose a new password:\n\n" + link
                                    + "\n\nThe link works once and expires in 1 hour. If you didn't ask for this, ignore this email; your password stays the same."));
                });
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        userFor(consume(token, AccountTokenPurpose.EMAIL_VERIFICATION)).setVerificationStatus(true);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        userFor(consume(token, AccountTokenPurpose.PASSWORD_RESET))
                .setPasswordHash(passwordEncoder.encode(newPassword));
    }

    // helpers //

    private boolean sentRecently(User user, AccountTokenPurpose purpose) {
        return accountTokenRepository.existsByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(), purpose, LocalDateTime.now().minus(RESEND_INTERVAL));
    }

    private String issueLink(User user, AccountTokenPurpose purpose, Duration lifetime, String path) {
        LocalDateTime now = LocalDateTime.now();
        accountTokenRepository.invalidateUnused(user.getId(), purpose, now);

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        AccountToken token = new AccountToken();
        token.setUserId(user.getId());
        token.setPurpose(purpose);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(now.plus(lifetime));
        accountTokenRepository.save(token);

        return frontendUrl.replaceAll("/+$", "") + path + "?token=" + rawToken;
    }

    private AccountToken consume(String rawToken, AccountTokenPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        AccountToken token = accountTokenRepository.findByTokenHashForUpdate(sha256(rawToken))
                .filter(t -> t.getPurpose() == purpose && t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new BusinessRuleViolationException(INVALID_LINK));
        token.setUsedAt(now);
        return token;
    }

    private User userFor(AccountToken token) {
        return userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BusinessRuleViolationException(INVALID_LINK));
    }

    private void mailAfterCommit(OutgoingMail mail) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                accountMailSender.send(mail);
            }
        });
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
