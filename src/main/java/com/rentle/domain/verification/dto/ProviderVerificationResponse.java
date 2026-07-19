package com.rentle.domain.verification.dto;

import com.rentle.domain.verification.model.ProviderVerification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProviderVerificationResponse(
        UUID id,
        UUID userId,
        UUID orgId,
        UUID categoryId,
        String status,
        Map<String, Object> fields,
        String rejectionReason,
        Instant createdAt
) {
    public static ProviderVerificationResponse from(ProviderVerification v) {
        return new ProviderVerificationResponse(v.getId(), v.getUserId(), v.getOrgId(), v.getCategoryId(),
                v.getStatus(), v.getFields(), v.getRejectionReason(), v.getCreatedAt());
    }
}
