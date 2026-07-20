package com.rentle.domain.trust.dto;

import com.rentle.domain.trust.model.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateReportRequest(
        @NotNull ReportTargetType targetType,
        @NotNull UUID targetId,
        @NotBlank @Size(max = 1000) String reason
) {}
