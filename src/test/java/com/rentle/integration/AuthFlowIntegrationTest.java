package com.rentle.integration;

import com.rentle.config.TestcontainersConfig;
import com.rentle.domain.user.dto.AuthResponse;
import com.rentle.domain.user.dto.LoginRequest;
import com.rentle.domain.user.dto.RegisterRequest;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.domain.user.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfig.class)
class AuthFlowIntegrationTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;

    private RegisterRequest uniqueRegisterRequest() {
        long n = System.nanoTime() % 1_000_000_000L;
        return new RegisterRequest("user" + n + "@test.com", "password123", "Test User");
    }

    @Test
    void registerCreatesAccountImmediatelyAndSendsEmailLink() {
        RegisterRequest req = uniqueRegisterRequest();
        AuthResponse auth = authService.register(req);

        assertNotNull(auth.accessToken());
        assertNotNull(auth.refreshToken());

        User user = userRepository.findByEmail(req.email()).orElseThrow();
        // Email-first: no phone yet, nothing verified — verification happens later.
        assertNull(user.getPhoneNumber());
        assertFalse(user.getPhoneVerified());
        assertFalse(user.getEmailVerified());
        assertEquals(UserStatus.PENDING_VERIFICATION, user.getStatus());
    }

    @Test
    void duplicateEmailRegistrationRejected() {
        RegisterRequest req = uniqueRegisterRequest();
        authService.register(req);
        RegisterRequest dup = new RegisterRequest(req.email(), "password123", "Dup User");
        assertThrows(RuntimeException.class, () -> authService.register(dup));
    }

    @Test
    void accountLocksAfterFiveFailedLogins() {
        RegisterRequest req = uniqueRegisterRequest();
        authService.register(req);

        for (int i = 0; i < 5; i++) {
            assertThrows(RuntimeException.class,
                    () -> authService.login(new LoginRequest(req.email(), "wrong-password")));
        }
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login(new LoginRequest(req.email(), req.password())));
        assertTrue(ex.getMessage().toLowerCase().contains("locked"));
    }

    @Test
    void loginByEmailSucceeds() {
        RegisterRequest req = uniqueRegisterRequest();
        authService.register(req);
        AuthResponse auth = authService.login(new LoginRequest(req.email(), req.password()));
        assertNotNull(auth.accessToken());
    }

    @Test
    void refreshTokensRotate() {
        AuthResponse initial = authService.register(uniqueRegisterRequest());

        AuthResponse refreshed = authService.refresh(initial.refreshToken());
        assertNotNull(refreshed.accessToken());
        assertNotEquals(initial.refreshToken(), refreshed.refreshToken());

        assertThrows(RuntimeException.class, () -> authService.refresh(initial.refreshToken()));
    }

    @Test
    void suspendedUserCannotLogin() {
        RegisterRequest req = uniqueRegisterRequest();
        authService.register(req);
        User user = userRepository.findByEmail(req.email()).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);

        assertThrows(RuntimeException.class,
                () -> authService.login(new LoginRequest(req.email(), req.password())));
    }
}
