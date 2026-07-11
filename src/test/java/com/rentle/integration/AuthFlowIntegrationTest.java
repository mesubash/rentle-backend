package com.rentle.integration;

import com.rentle.config.TestcontainersConfig;
import com.rentle.domain.user.dto.AuthResponse;
import com.rentle.domain.user.dto.LoginRequest;
import com.rentle.domain.user.dto.RegisterRequest;
import com.rentle.domain.user.dto.RegistrationResponse;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.domain.user.service.AuthService;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfig.class)
class AuthFlowIntegrationTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired StringRedisTemplate redis;

    private RegisterRequest uniqueRegisterRequest() {
        long n = System.nanoTime() % 1_000_000_000L;
        return new RegisterRequest("+97798" + n, "user" + n + "@test.com", "password123", "Test User");
    }

    /** Full two-step signup: register (OTP only) then verify to create the account. */
    private AuthResponse signUp(RegisterRequest req) {
        authService.register(req);
        String code = redis.opsForValue().get("otp:" + req.phoneNumber());
        assertNotNull(code, "OTP must be issued on registration");
        return authService.completeRegistration(req.phoneNumber(), code);
    }

    @Test
    void registrationRequiresPhoneOtpBeforeAccountExists() {
        RegisterRequest req = uniqueRegisterRequest();
        RegistrationResponse pending = authService.register(req);

        assertTrue(pending.otpRequired());
        // No account and no tokens until the OTP is verified — the critical guarantee.
        assertTrue(userRepository.findByPhoneNumber(req.phoneNumber()).isEmpty());

        String code = redis.opsForValue().get("otp:" + req.phoneNumber());
        AuthResponse auth = authService.completeRegistration(req.phoneNumber(), code);

        assertNotNull(auth.accessToken());
        assertNotNull(auth.refreshToken());
        User user = userRepository.findByPhoneNumber(req.phoneNumber()).orElseThrow();
        assertTrue(user.getPhoneVerified());
        assertEquals(false, user.getEmailVerified()); // email verified later via link
    }

    @Test
    void wrongOtpDoesNotCreateAccount() {
        RegisterRequest req = uniqueRegisterRequest();
        authService.register(req);
        assertThrows(RentleException.class,
                () -> authService.completeRegistration(req.phoneNumber(), "000000"));
        assertTrue(userRepository.findByPhoneNumber(req.phoneNumber()).isEmpty());
    }

    @Test
    void duplicatePhoneRegistrationRejected() {
        RegisterRequest req = uniqueRegisterRequest();
        signUp(req);
        RegisterRequest dup = new RegisterRequest(
                req.phoneNumber(), "other" + System.nanoTime() + "@test.com", "password123", "Dup User");
        assertThrows(RuntimeException.class, () -> authService.register(dup));
    }

    @Test
    void accountLocksAfterFiveFailedLogins() {
        RegisterRequest req = uniqueRegisterRequest();
        signUp(req);

        for (int i = 0; i < 5; i++) {
            assertThrows(UnauthorizedException.class,
                    () -> authService.login(new LoginRequest(req.phoneNumber(), "wrong-password")));
        }
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest(req.phoneNumber(), req.password())));
        assertTrue(ex.getMessage().toLowerCase().contains("locked"));
    }

    @Test
    void refreshTokensRotate() {
        AuthResponse initial = signUp(uniqueRegisterRequest());

        AuthResponse refreshed = authService.refresh(initial.refreshToken());
        assertNotNull(refreshed.accessToken());
        assertNotEquals(initial.refreshToken(), refreshed.refreshToken());

        assertThrows(UnauthorizedException.class, () -> authService.refresh(initial.refreshToken()));
    }

    @Test
    void suspendedUserCannotLogin() {
        RegisterRequest req = uniqueRegisterRequest();
        signUp(req);
        User user = userRepository.findByPhoneNumber(req.phoneNumber()).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        assertThrows(UnauthorizedException.class,
                () -> authService.login(new LoginRequest(req.phoneNumber(), req.password())));
    }
}
