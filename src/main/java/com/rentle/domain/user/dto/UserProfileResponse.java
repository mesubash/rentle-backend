package com.rentle.domain.user.dto;

import com.rentle.domain.user.model.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Full profile — returned only to the user themself (or admins). */
public record UserProfileResponse(
        UUID id,
        String phoneNumber,
        String email,
        String fullName,
        String profilePhotoUrl,
        String status,
        String authProvider,      // LOCAL | GOOGLE
        boolean hasPassword,
        boolean phoneVerified,
        boolean emailVerified,
        boolean citizenshipVerified,
        String kycStatus,         // null | SUBMITTED | APPROVED | REJECTED
        BigDecimal trustScore,
        Instant createdAt
) {
    public static UserProfileResponse from(User u) {
        return from(u, null);
    }

    public static UserProfileResponse from(User u, String kycStatus) {
        return new UserProfileResponse(
                u.getId(),
                u.getPhoneNumber(),
                u.getEmail(),
                u.getFullName(),
                u.getProfilePhotoUrl(),
                u.getStatus().name(),
                u.getGoogleId() != null ? "GOOGLE" : "LOCAL",
                u.getPasswordHash() != null,
                Boolean.TRUE.equals(u.getPhoneVerified()),
                Boolean.TRUE.equals(u.getEmailVerified()),
                Boolean.TRUE.equals(u.getCitizenshipVerified()),
                kycStatus,
                u.getTrustScore(),
                u.getCreatedAt()
        );
    }
}
