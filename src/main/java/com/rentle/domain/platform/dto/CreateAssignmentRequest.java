package com.rentle.domain.platform.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAssignmentRequest(
        @NotNull UUID userId,
        @NotNull UUID roleId
) {}
