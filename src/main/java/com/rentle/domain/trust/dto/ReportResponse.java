package com.rentle.domain.trust.dto;

import com.rentle.domain.trust.model.Report;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID reporterId,
        String reporterName,
        String targetType,
        UUID targetId,
        String reason,
        String status,
        String resolutionNote,
        UUID handledBy,
        Instant handledAt,
        Instant createdAt
) {
    public static ReportResponse from(Report r) {
        return new ReportResponse(
                r.getId(),
                r.getReporter().getId(),
                r.getReporter().getFullName(),
                r.getTargetType().name(),
                r.getTargetId(),
                r.getReason(),
                r.getStatus().name(),
                r.getResolutionNote(),
                r.getHandledBy(),
                r.getHandledAt(),
                r.getCreatedAt()
        );
    }
}
