package com.rentle.domain.user.service;

import com.rentle.config.RentleProperties;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.notification.EmailService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Email verification by clickable link (not OTP): a one-time token maps to the
 * user for 24 hours; opening the link verifies the address. Email verification
 * can happen any time after registration, so it never blocks sign-up.
 */
@Service
public class EmailVerificationService {

    private static final String PREFIX = "emailverify:";
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final RentleProperties props;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(StringRedisTemplate redis,
                                    EmailService emailService,
                                    UserRepository userRepository,
                                    RentleProperties props) {
        this.redis = redis;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.props = props;
    }

    /** Issue a fresh token and email the verification link. */
    public void sendLink(User user) {
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new RentleException("Email already verified");
        }
        String token = newToken();
        redis.opsForValue().set(PREFIX + token, user.getId().toString(), TOKEN_TTL);
        // Points at the backend endpoint, which verifies the token and then
        // redirects to the app's result page.
        String link = props.apiBaseUrl() + "/api/v1/auth/verify-email?token=" + token;
        emailService.send(user.getEmail(), "Verify your Rentle email",
                "Confirm your email address by opening this link (valid 24 hours):\n" + link);
    }

    public void sendLinkToCurrentUser(UUID userId) {
        sendLink(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    /** Consume a token and mark the email verified. Idempotent-ish: an expired or
     *  reused token is rejected, but re-verifying an already-verified user is fine. */
    @Transactional
    public void verifyToken(String token) {
        String userId = redis.opsForValue().getAndDelete(PREFIX + token);
        if (userId == null) {
            throw new RentleException("This verification link is invalid or has expired");
        }
        userRepository.findById(UUID.fromString(userId)).ifPresent(user -> {
            user.setEmailVerified(true);
            userRepository.save(user);
        });
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
