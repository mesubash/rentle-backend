package com.rentle.domain.user.dto;

import com.rentle.domain.user.model.KycDetail;

import java.time.Instant;
import java.util.UUID;

/** Compact row for the admin KYC review queue. */
public record KycAdminRow(
        UUID userId,
        String currentName,
        String realName,
        String email,
        String status,
        Instant submittedAt
) {
    public static KycAdminRow from(KycDetail k) {
        return new KycAdminRow(
                k.getUser().getId(),
                k.getUser().getFullName(),
                k.getRealName(),
                k.getUser().getEmail(),
                k.getStatus().name(),
                k.getCreatedAt());
    }
}
