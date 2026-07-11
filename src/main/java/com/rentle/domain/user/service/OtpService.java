package com.rentle.domain.user.service;

import com.rentle.config.RentleProperties;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.notification.EmailService;
import com.rentle.shared.notification.SmsService;
import com.rentle.shared.security.RateLimitService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class OtpService {

    private static final int OTP_PER_HOUR = 5;
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");

    private final StringRedisTemplate redis;
    private final SmsService smsService;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final UserRepository userRepository;
    private final RentleProperties props;
    private final SecureRandom random = new SecureRandom();

    public OtpService(StringRedisTemplate redis,
                      SmsService smsService,
                      EmailService emailService,
                      RateLimitService rateLimitService,
                      UserRepository userRepository,
                      RentleProperties props) {
        this.redis = redis;
        this.smsService = smsService;
        this.emailService = emailService;
        this.rateLimitService = rateLimitService;
        this.userRepository = userRepository;
        this.props = props;
    }

    // --- Phone OTP: register-time flow, keyed by the phone already on the account ---

    public void sendOtp(String phoneNumber) {
        if (!userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ResourceNotFoundException("No account with this phone number");
        }
        dispatchPhone(phoneNumber);
    }

    @Transactional
    public void verifyOtp(String phoneNumber, String code) {
        checkCode("otp:" + phoneNumber, code);
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPhoneVerified(true);
        userRepository.save(user);
    }

    // --- Phone OTP: current-user flow (Google users adding a phone, or changing it) ---

    @Transactional
    public void setPhoneAndSendOtp(UUID userId, String phoneNumber) {
        if (!PHONE.matcher(phoneNumber).matches()) {
            throw new RentleException("Invalid phone number");
        }
        User user = getUser(userId);
        userRepository.findByPhoneNumber(phoneNumber)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> { throw new RentleException("Phone number already in use"); });

        if (!phoneNumber.equals(user.getPhoneNumber())) {
            user.setPhoneNumber(phoneNumber);
            user.setPhoneVerified(false);
            userRepository.save(user);
        }
        dispatchPhone(phoneNumber);
    }

    @Transactional
    public void verifyCurrentUserPhone(UUID userId, String code) {
        User user = getUser(userId);
        if (user.getPhoneNumber() == null) {
            throw new RentleException("Add a phone number first");
        }
        checkCode("otp:" + user.getPhoneNumber(), code);
        user.setPhoneVerified(true);
        userRepository.save(user);
    }

    // --- Email OTP: current-user flow ---

    public void sendEmailOtp(UUID userId) {
        User user = getUser(userId);
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new RentleException("Email already verified");
        }
        if (!rateLimitService.allow("emailotp:" + userId, OTP_PER_HOUR, Duration.ofHours(1))) {
            throw new RentleException("Too many verification requests. Try again later.");
        }
        String code = newCode();
        redis.opsForValue().set("emailotp:" + userId, code, Duration.ofMinutes(props.otpExpiryMinutes()));
        emailService.send(user.getEmail(), "Verify your Rentle email",
                "Your Rentle email verification code is " + code
                        + ". It expires in " + props.otpExpiryMinutes() + " minutes.");
    }

    @Transactional
    public void verifyEmailOtp(UUID userId, String code) {
        checkCode("emailotp:" + userId, code);
        User user = getUser(userId);
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    // --- helpers ---

    private void dispatchPhone(String phoneNumber) {
        if (!rateLimitService.allow("otp:" + phoneNumber, OTP_PER_HOUR, Duration.ofHours(1))) {
            throw new RentleException("Too many OTP requests. Try again in an hour.");
        }
        String code = newCode();
        redis.opsForValue().set("otp:" + phoneNumber, code, Duration.ofMinutes(props.otpExpiryMinutes()));
        smsService.send(phoneNumber, "Your Rentle verification code is " + code
                + ". Valid for " + props.otpExpiryMinutes() + " minutes.");
    }

    private void checkCode(String key, String code) {
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(code)) {
            throw new RentleException("Invalid or expired code");
        }
        redis.delete(key);
    }

    private String newCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
