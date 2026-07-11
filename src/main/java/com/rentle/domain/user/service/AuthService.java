package com.rentle.domain.user.service;

import com.rentle.config.JwtProperties;
import com.rentle.domain.user.dto.AuthResponse;
import com.rentle.domain.user.dto.LoginRequest;
import com.rentle.domain.user.dto.RegisterRequest;
import com.rentle.domain.user.dto.RegistrationResponse;
import com.rentle.domain.user.dto.UserProfileResponse;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.TooManyRequestsException;
import com.rentle.shared.exception.UnauthorizedException;
import com.rentle.shared.security.GoogleTokenVerifier;
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
    private static final String PENDING_PREFIX = "pendingreg:";
    private static final Duration PENDING_TTL = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redis;
    private final OtpService otpService;
    private final EmailVerificationService emailVerificationService;
    private final RateLimitService rateLimitService;
    private final GoogleTokenVerifier googleTokenVerifier;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       JwtProperties jwtProperties,
                       StringRedisTemplate redis,
                       OtpService otpService,
                       EmailVerificationService emailVerificationService,
                       RateLimitService rateLimitService,
                       GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.redis = redis;
        this.otpService = otpService;
        this.emailVerificationService = emailVerificationService;
        this.rateLimitService = rateLimitService;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    /**
     * Step 1 of registration: validate, stash the pending signup, and send a phone
     * OTP. No account or token is created until {@link #completeRegistration} — an
     * unverified phone can never become a usable account.
     */
    public RegistrationResponse register(RegisterRequest req) {
        if (userRepository.existsByPhoneNumber(req.phoneNumber())) {
            throw new RentleException("Phone number already registered");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new RentleException("Email already registered");
        }

        String key = PENDING_PREFIX + req.phoneNumber();
        redis.opsForHash().putAll(key, java.util.Map.of(
                "email", req.email(),
                "passwordHash", passwordEncoder.encode(req.password()),
                "fullName", req.fullName()));
        redis.expire(key, PENDING_TTL);

        otpService.sendPhoneCode(req.phoneNumber());
        return RegistrationResponse.otpSent(req.phoneNumber(), PENDING_TTL.toSeconds());
    }

    /** Step 2: verify the phone OTP, then create the account and issue tokens. */
    @Transactional
    public AuthResponse completeRegistration(String phoneNumber, String code) {
        otpService.assertPhoneCode(phoneNumber, code);

        String key = PENDING_PREFIX + phoneNumber;
        var pending = redis.<String, String>opsForHash().entries(key);
        if (pending.isEmpty()) {
            throw new RentleException("Registration expired. Please start again.");
        }
        String email = pending.get("email");
        if (userRepository.existsByPhoneNumber(phoneNumber) || userRepository.existsByEmail(email)) {
            redis.delete(key);
            throw new RentleException("This phone or email is already registered");
        }

        User user = new User();
        user.setPhoneNumber(phoneNumber);
        user.setEmail(email);
        user.setPasswordHash(pending.get("passwordHash"));
        user.setFullName(pending.get("fullName"));
        user.setPhoneVerified(true);
        user = userRepository.save(user);
        redis.delete(key);

        // Email is verified later by clicking a link; it never blocks registration.
        emailVerificationService.sendLink(user);
        return issueTokens(user);
    }

    /** Resend the registration OTP while a pending signup exists. */
    public void resendRegistrationOtp(String phoneNumber) {
        if (Boolean.FALSE.equals(redis.hasKey(PENDING_PREFIX + phoneNumber))) {
            throw new RentleException("No pending registration for this number");
        }
        otpService.sendPhoneCode(phoneNumber);
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

        // Social-login accounts have no password
        if (user.getPasswordHash() == null) {
            throw new UnauthorizedException("Use Google sign-in for this account");
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

    /** Create or link the account for a verified Google identity (no tokens issued). */
    @Transactional
    public User resolveGoogleUser(String idToken) {
        GoogleTokenVerifier.GoogleIdentity identity = googleTokenVerifier.verify(idToken);

        // Match on Google id first, then link to an existing account by email
        // (Google having verified the email proves ownership), else create one.
        User user = userRepository.findByGoogleId(identity.subject())
                .orElseGet(() -> userRepository.findByEmail(identity.email()).orElse(null));

        if (user == null) {
            user = new User();
            user.setEmail(identity.email());
            user.setFullName(identity.name() != null && !identity.name().isBlank()
                    ? identity.name() : identity.email());
            user.setProfilePhotoUrl(identity.picture());
        }
        user.setGoogleId(identity.subject());
        if (identity.emailVerified()) {
            user.setEmailVerified(true);
        }
        return userRepository.save(user);
    }

    /** Issue a session for a resolved user id (used after the OAuth handoff). */
    @Transactional(readOnly = true)
    public AuthResponse issueSessionFor(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("Account suspended. Contact support.");
        }
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
