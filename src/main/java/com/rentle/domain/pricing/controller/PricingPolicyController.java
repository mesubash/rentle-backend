package com.rentle.domain.pricing.controller;

import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.pricing.dto.PricingPolicyRequest;
import com.rentle.domain.pricing.dto.PricingPolicyResponse;
import com.rentle.domain.pricing.service.PricingPolicyService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PricingPolicyController {

    private final PricingPolicyService service;

    public PricingPolicyController(PricingPolicyService service) {
        this.service = service;
    }

    /** Public: a category's pricing policy (deposit guidance + cancellation schedule) for display. */
    @GetMapping("/categories/{id}/pricing-policy")
    public ApiResponse<PricingPolicyResponse> policy(@PathVariable UUID id) {
        return ApiResponse.ok(service.find(id).map(PricingPolicyResponse::from)
                .orElseGet(() -> PricingPolicyResponse.empty(id)));
    }

    /** Admin: set a category's pricing policy. */
    @PutMapping("/admin/categories/{id}/pricing-policy")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LISTING_CATEGORY_MANAGE + "')")
    public ApiResponse<PricingPolicyResponse> save(@PathVariable UUID id,
                                                   @Valid @RequestBody PricingPolicyRequest request) {
        return ApiResponse.ok(PricingPolicyResponse.from(
                service.upsert(id, request, SecurityUtils.currentUserId())));
    }
}
