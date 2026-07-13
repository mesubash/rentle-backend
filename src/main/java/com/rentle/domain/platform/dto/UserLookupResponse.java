package com.rentle.domain.platform.dto;

import com.rentle.domain.user.model.User;

import java.util.UUID;

public record UserLookupResponse(
        UUID id,
        String email,
        String fullName,
        String status
) {
    public static UserLookupResponse from(User user) {
        return new UserLookupResponse(user.getId(), user.getEmail(), user.getFullName(), user.getStatus().name());
    }
}
