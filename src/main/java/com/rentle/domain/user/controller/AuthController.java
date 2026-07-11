package com.rentle.domain.user.controller;

import com.rentle.domain.user.dto.AuthResponse;
import com.rentle.domain.user.dto.GoogleLoginRequest;
import com.rentle.domain.user.dto.LoginRequest;
import com.rentle.domain.user.dto.OtpSendRequest;
import com.rentle.domain.user.dto.OtpVerifyRequest;
import com.rentle.domain.user.dto.RefreshRequest;
import com.rentle.domain.user.dto.RegisterRequest;
import com.rentle.domain.user.service.AuthService;
import com.rentle.domain.user.service.OtpService;
import com.rentle.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    public AuthController(AuthService authService, OtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ApiResponse<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ApiResponse.ok(authService.loginWithGoogle(request.idToken()));
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

    @PostMapping("/otp/send")
    public ApiResponse<String> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        otpService.sendOtp(request.phoneNumber());
        return ApiResponse.ok("OTP sent");
    }

    @PostMapping("/otp/verify")
    public ApiResponse<String> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        otpService.verifyOtp(request.phoneNumber(), request.code());
        return ApiResponse.ok("Phone verified");
    }
}
