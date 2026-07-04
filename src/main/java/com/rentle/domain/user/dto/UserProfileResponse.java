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
        String role,
        String status,
        boolean phoneVerified,
        boolean citizenshipVerified,
        boolean citizenshipUploaded,
        BigDecimal trustScore,
        Instant createdAt
) {
    public static UserProfileResponse from(User u) {
        return new UserProfileResponse(
                u.getId(),
                u.getPhoneNumber(),
                u.getEmail(),
                u.getFullName(),
                u.getProfilePhotoUrl(),
                u.getRole().name(),
                u.getStatus().name(),
                Boolean.TRUE.equals(u.getPhoneVerified()),
                Boolean.TRUE.equals(u.getCitizenshipVerified()),
                u.getCitizenshipCardUrl() != null,
                u.getTrustScore(),
                u.getCreatedAt()
        );
    }
}
