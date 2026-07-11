package com.rentle.domain.user.service;

import com.rentle.config.RentleProperties;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.notification.SmsService;
import com.rentle.shared.security.RateLimitService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/** Phone-number OTP: registration gating and the current-user add/change-phone flow. */
@Service
public class OtpService {

    private static final int OTP_PER_HOUR = 5;
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");

    private final StringRedisTemplate redis;
    private final SmsService smsService;
    private final RateLimitService rateLimitService;
    private final UserRepository userRepository;
    private final RentleProperties props;
    private final SecureRandom random = new SecureRandom();

    public OtpService(StringRedisTemplate redis,
                      SmsService smsService,
                      RateLimitService rateLimitService,
                      UserRepository userRepository,
                      RentleProperties props) {
        this.redis = redis;
        this.smsService = smsService;
        this.rateLimitService = rateLimitService;
        this.userRepository = userRepository;
        this.props = props;
    }

    // --- low-level, used by the registration flow (no account exists yet) ---

    /** Send an OTP to a phone number, independent of any account. */
    public void sendPhoneCode(String phoneNumber) {
        if (!rateLimitService.allow("otp:" + phoneNumber, OTP_PER_HOUR, Duration.ofHours(1))) {
            throw new RentleException("Too many code requests. Try again in an hour.");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set("otp:" + phoneNumber, code, Duration.ofMinutes(props.otpExpiryMinutes()));
        smsService.send(phoneNumber, "Your Rentle verification code is " + code
                + ". Valid for " + props.otpExpiryMinutes() + " minutes.");
    }

    /** Validate and consume a phone OTP (throws if wrong/expired). */
    public void assertPhoneCode(String phoneNumber, String code) {
        String stored = redis.opsForValue().get("otp:" + phoneNumber);
        if (stored == null || !stored.equals(code)) {
            throw new RentleException("Invalid or expired code");
        }
        redis.delete("otp:" + phoneNumber);
    }

    // --- current-user phone: Google users adding a phone, or anyone changing it ---

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
        sendPhoneCode(phoneNumber);
    }

    @Transactional
    public void verifyCurrentUserPhone(UUID userId, String code) {
        User user = getUser(userId);
        if (user.getPhoneNumber() == null) {
            throw new RentleException("Add a phone number first");
        }
        assertPhoneCode(user.getPhoneNumber(), code);
        user.setPhoneVerified(true);
        userRepository.save(user);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
