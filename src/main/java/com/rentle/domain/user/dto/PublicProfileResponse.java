package com.rentle.domain.user.dto;

import com.rentle.domain.user.model.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public profile — no phone, email or citizenship data. */
public record PublicProfileResponse(
        UUID id,
        String fullName,
        String profilePhotoUrl,
        boolean verified,
        BigDecimal trustScore,
        Instant memberSince
) {
    public static PublicProfileResponse from(User u) {
        return new PublicProfileResponse(
                u.getId(),
                u.getFullName(),
                u.getProfilePhotoUrl(),
                Boolean.TRUE.equals(u.getCitizenshipVerified()),
                u.getTrustScore(),
                u.getCreatedAt()
        );
    }
}
