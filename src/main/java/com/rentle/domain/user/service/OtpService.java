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

@Service
public class OtpService {

    private static final int OTP_PER_HOUR = 3;

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

    public void sendOtp(String phoneNumber) {
        if (!userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ResourceNotFoundException("No account with this phone number");
        }
        if (!rateLimitService.allow("otp:" + phoneNumber, OTP_PER_HOUR, Duration.ofHours(1))) {
            throw new RentleException("Too many OTP requests. Try again in an hour.");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set("otp:" + phoneNumber, code, Duration.ofMinutes(props.otpExpiryMinutes()));
        smsService.send(phoneNumber, "Your Rentle verification code is " + code
                + ". Valid for " + props.otpExpiryMinutes() + " minutes.");
    }

    @Transactional
    public void verifyOtp(String phoneNumber, String code) {
        String stored = redis.opsForValue().get("otp:" + phoneNumber);
        if (stored == null || !stored.equals(code)) {
            throw new RentleException("Invalid or expired OTP");
        }
        redis.delete("otp:" + phoneNumber);

        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setPhoneVerified(true);
        userRepository.save(user);
    }
}
