package com.rentle.domain.verification.controller;

import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.user.dto.ReasonRequest;
import com.rentle.domain.verification.dto.ProviderVerificationResponse;
import com.rentle.domain.verification.dto.SubmitVerificationRequest;
import com.rentle.domain.verification.service.ProviderVerificationService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProviderVerificationController {

    private final ProviderVerificationService service;

    public ProviderVerificationController(ProviderVerificationService service) {
        this.service = service;
    }

    /** Provider submits (or resubmits) credentials for a category. */
    @PostMapping("/users/me/provider-verifications")
    public ApiResponse<ProviderVerificationResponse> submit(@Valid @RequestBody SubmitVerificationRequest request) {
        return ApiResponse.ok(service.submit(SecurityUtils.currentUserId(), request));
    }

    /** The provider's own submissions, or an organization's when orgId is given. */
    @GetMapping("/users/me/provider-verifications")
    public ApiResponse<List<ProviderVerificationResponse>> mine(@RequestParam(required = false) UUID orgId) {
        UUID userId = SecurityUtils.currentUserId();
        return ApiResponse.ok(orgId != null ? service.forOrg(userId, orgId) : service.mine(userId));
    }

    /** Admin review queue. */
    @GetMapping("/admin/provider-verifications")
    @PreAuthorize("hasAuthority('" + PermissionKeys.KYC_SUBMISSION_READ + "')")
    public ApiResponse<PageResponse<ProviderVerificationResponse>> queue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(service.queue(status, pageable));
    }

    @PutMapping("/admin/provider-verifications/{id}/approve")
    @PreAuthorize("hasAuthority('" + PermissionKeys.KYC_SUBMISSION_APPROVE + "')")
    public ApiResponse<ProviderVerificationResponse> approve(@PathVariable UUID id) {
        return ApiResponse.ok(service.decide(SecurityUtils.currentUserId(), id, true, null));
    }

    @PutMapping("/admin/provider-verifications/{id}/reject")
    @PreAuthorize("hasAuthority('" + PermissionKeys.KYC_SUBMISSION_REJECT + "')")
    public ApiResponse<ProviderVerificationResponse> reject(@PathVariable UUID id,
                                                            @Valid @RequestBody(required = false) ReasonRequest request) {
        return ApiResponse.ok(service.decide(SecurityUtils.currentUserId(), id, false,
                request != null ? request.reason() : null));
    }
}
