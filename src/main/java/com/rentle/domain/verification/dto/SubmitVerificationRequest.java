package com.rentle.domain.verification.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record SubmitVerificationRequest(
        @NotNull UUID categoryId,
        Map<String, Object> fields
) {}
