package com.rentle.domain.user.controller;

import com.rentle.domain.user.dto.KycResponse;
import com.rentle.domain.user.dto.KycSubmitRequest;
import com.rentle.domain.user.service.KycService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    /** Current user's KYC record (null if never submitted). */
    @GetMapping
    public ApiResponse<KycResponse> myKyc() {
        return ApiResponse.ok(kycService.myKyc(SecurityUtils.currentUserId()));
    }

    /** Submit (or resubmit after rejection) identity details with front/back images. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<KycResponse> submit(@Valid @ModelAttribute KycSubmitRequest request,
                                           @RequestParam("front") MultipartFile front,
                                           @RequestParam("back") MultipartFile back) {
        return ApiResponse.ok(kycService.submit(SecurityUtils.currentUserId(), request, front, back));
    }
}
