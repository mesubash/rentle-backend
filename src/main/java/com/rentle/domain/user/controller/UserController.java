package com.rentle.domain.user.controller;

import com.rentle.domain.user.dto.CodeRequest;
import com.rentle.domain.user.dto.PublicProfileResponse;
import com.rentle.domain.user.dto.SetPhoneRequest;
import com.rentle.domain.user.dto.UpdateProfileRequest;
import com.rentle.domain.user.dto.UserProfileResponse;
import com.rentle.domain.user.service.OtpService;
import com.rentle.domain.user.service.UserService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final OtpService otpService;

    public UserController(UserService userService, OtpService otpService) {
        this.userService = userService;
        this.otpService = otpService;
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me() {
        return ApiResponse.ok(userService.getMe(SecurityUtils.currentUserId()));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(SecurityUtils.currentUserId(), request));
    }

    @PostMapping("/me/photo")
    public ApiResponse<UserProfileResponse> uploadPhoto(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(userService.uploadProfilePhoto(SecurityUtils.currentUserId(), file));
    }

    @PostMapping("/me/citizenship")
    public ApiResponse<UserProfileResponse> uploadCitizenship(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(userService.uploadCitizenshipCard(SecurityUtils.currentUserId(), file));
    }

    @PostMapping("/me/phone")
    public ApiResponse<String> setPhone(@Valid @RequestBody SetPhoneRequest request) {
        otpService.setPhoneAndSendOtp(SecurityUtils.currentUserId(), request.phoneNumber());
        return ApiResponse.ok("Verification code sent");
    }

    @PostMapping("/me/phone/verify")
    public ApiResponse<UserProfileResponse> verifyPhone(@Valid @RequestBody CodeRequest request) {
        otpService.verifyCurrentUserPhone(SecurityUtils.currentUserId(), request.code());
        return ApiResponse.ok(userService.getMe(SecurityUtils.currentUserId()));
    }

    @PostMapping("/me/email/otp/send")
    public ApiResponse<String> sendEmailOtp() {
        otpService.sendEmailOtp(SecurityUtils.currentUserId());
        return ApiResponse.ok("Verification code sent");
    }

    @PostMapping("/me/email/otp/verify")
    public ApiResponse<UserProfileResponse> verifyEmail(@Valid @RequestBody CodeRequest request) {
        otpService.verifyEmailOtp(SecurityUtils.currentUserId(), request.code());
        return ApiResponse.ok(userService.getMe(SecurityUtils.currentUserId()));
    }

    @GetMapping("/me/citizenship")
    public ResponseEntity<Resource> myCitizenship() {
        UserService.CitizenshipFile file = userService.loadCitizenship(SecurityUtils.currentUserId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.resource());
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicProfileResponse> publicProfile(@PathVariable UUID id) {
        return ApiResponse.ok(userService.getPublicProfile(id));
    }
}
