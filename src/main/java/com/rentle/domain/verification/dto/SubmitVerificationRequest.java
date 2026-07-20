package com.rentle.domain.verification.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record SubmitVerificationRequest(
        @NotNull UUID categoryId,
        /** When set, submit the verification for this organization (caller must be a member allowed to list). */
        UUID orgId,
        Map<String, Object> fields
) {}
