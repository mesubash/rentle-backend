package com.rentle.domain.user.controller;

import com.rentle.config.RentleProperties;
import com.rentle.domain.user.dto.AuthResponse;
import com.rentle.domain.user.dto.LoginRequest;
import com.rentle.domain.user.dto.RefreshRequest;
import com.rentle.domain.user.dto.RegisterRequest;
import com.rentle.domain.user.service.AuthService;
import com.rentle.domain.user.service.EmailVerificationService;
import com.rentle.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final RentleProperties props;

    public AuthController(AuthService authService,
                          EmailVerificationService emailVerificationService,
                          RentleProperties props) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.props = props;
    }

    /** Email-first signup: creates the account and starts a session immediately. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(@RequestBody(required = false) RefreshRequest request) {
        Jwt jwt = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt j) {
            jwt = j;
        }
        authService.logout(request != null ? request.refreshToken() : null, jwt);
        return ApiResponse.ok("Logged out");
    }

    /** Opened from the email link; verifies then redirects back into the app. */
    @GetMapping("/verify-email")
    public RedirectView verifyEmail(@RequestParam("token") String token) {
        try {
            emailVerificationService.verifyToken(token);
            return new RedirectView(props.appUrl() + "/auth/verify-email?status=success");
        } catch (RuntimeException ex) {
            return new RedirectView(props.appUrl() + "/auth/verify-email?status=invalid");
        }
    }
}
