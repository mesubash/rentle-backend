package com.rentle.domain.user.service;

import com.rentle.config.RentleProperties;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.notification.EmailService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Self-service password reset by clickable link. Mirrors {@link EmailVerificationService}:
 * a one-time token maps to the user in Redis for one hour. Requesting a reset never
 * reveals whether an email exists (always returns quietly), and social-login accounts
 * with no password cannot reset.
 */
@Service
public class PasswordResetService {

    private static final String PREFIX = "pwreset:";
    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RentleProperties props;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(StringRedisTemplate redis,
                                EmailService emailService,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                RentleProperties props) {
        this.redis = redis;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    /** Issue a token and email the reset link. Silent if the email is unknown or passwordless. */
    public void requestReset(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (user.getPasswordHash() == null) {
                return; // Google-only account: nothing to reset
            }
            String token = newToken();
            redis.opsForValue().set(PREFIX + token, user.getId().toString(), TOKEN_TTL);
            String link = props.appUrl() + "/auth/reset-password?token=" + token;
            emailService.send(user.getEmail(), "Reset your Rentle password",
                    "Open this link to set a new password (valid 1 hour):\n" + link
                            + "\n\nIf you did not request this, ignore this email.");
        });
    }

    /** Consume a token and set the new password. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72) {
            throw new RentleException("Password must be 8-72 characters");
        }
        String userId = redis.opsForValue().getAndDelete(PREFIX + token);
        if (userId == null) {
            throw new RentleException("This reset link is invalid or has expired");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RentleException("This reset link is invalid or has expired"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
