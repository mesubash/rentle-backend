package com.rentle.domain.user.service;

import com.rentle.config.JwtProperties;
import com.rentle.domain.user.dto.AuthResponse;
import com.rentle.domain.user.dto.LoginRequest;
import com.rentle.domain.user.dto.RegisterRequest;
import com.rentle.domain.user.dto.UserProfileResponse;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.TooManyRequestsException;
import com.rentle.shared.exception.UnauthorizedException;
import com.rentle.shared.notification.EmailService;
import com.rentle.shared.security.JwtTokenService;
import com.rentle.shared.security.RateLimitService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOGIN_ATTEMPTS_PER_WINDOW = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String REFRESH_PREFIX = "refresh:";
    private static final String BLACKLIST_PREFIX = "bl:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redis;
    private final OtpService otpService;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       JwtProperties jwtProperties,
                       StringRedisTemplate redis,
                       OtpService otpService,
                       EmailService emailService,
                       RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.redis = redis;
        this.otpService = otpService;
        this.emailService = emailService;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByPhoneNumber(req.phoneNumber())) {
            throw new RentleException("Phone number already registered");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new RentleException("Email already registered");
        }

        User user = new User();
        user.setPhoneNumber(req.phoneNumber());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        user = userRepository.save(user);

        otpService.sendOtp(user.getPhoneNumber());
        emailService.send(user.getEmail(), "Welcome to Rentle",
                "Hi " + user.getFullName() + ", welcome to Rentle! Verify your phone to get started.");

        return issueTokens(user);
    }

    // noRollbackFor: the failed-attempt counter must survive the thrown
    // UnauthorizedException, otherwise account lockout never triggers
    @Transactional(noRollbackFor = {UnauthorizedException.class, TooManyRequestsException.class})
    public AuthResponse login(LoginRequest req) {
        // Account-scoped attempt cap — robust behind a single-IP BFF where a
        // per-IP limit would either lock out everyone or nobody.
        if (!rateLimitService.allow("login:" + req.identifier().toLowerCase(),
                LOGIN_ATTEMPTS_PER_WINDOW, LOGIN_WINDOW)) {
            throw new TooManyRequestsException("Too many login attempts. Try again later.");
        }

        User user = userRepository
                .findByPhoneNumberOrEmail(req.identifier(), req.identifier())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new UnauthorizedException("Account locked due to failed logins. Try again later.");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
                user.setFailedLoginAttempts(0);
            }
            userRepository.save(user);
            throw new UnauthorizedException("Invalid credentials");
        }

        // SUSPENDED enforced at token generation time, not just controller level
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("Account suspended. Contact support.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokens(user);
    }

    public AuthResponse refresh(String refreshToken) {
        // Atomic get-and-delete: two concurrent requests with the same token
        // can't both pass, so rotation is genuinely single-use.
        String userId = redis.opsForValue().getAndDelete(REFRESH_PREFIX + refreshToken);
        if (userId == null) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("Account suspended. Contact support.");
        }
        return issueTokens(user);
    }

    public void logout(String refreshToken, Jwt accessJwt) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            redis.delete(REFRESH_PREFIX + refreshToken);
        }
        if (accessJwt != null && accessJwt.getId() != null && accessJwt.getExpiresAt() != null) {
            long ttl = Duration.between(Instant.now(), accessJwt.getExpiresAt()).getSeconds();
            if (ttl > 0) {
                redis.opsForValue().set(BLACKLIST_PREFIX + accessJwt.getId(), "1", Duration.ofSeconds(ttl));
            }
        }
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenService.createAccessToken(
                user.getId(), user.getRole().name(), user.getStatus().name());

        String refreshToken = UUID.randomUUID().toString();
        redis.opsForValue().set(
                REFRESH_PREFIX + refreshToken,
                user.getId().toString(),
                Duration.ofMillis(jwtProperties.refreshTokenExpiryMs()));

        return AuthResponse.of(accessToken, refreshToken,
                jwtTokenService.accessTokenExpirySeconds(), UserProfileResponse.from(user));
    }
}
