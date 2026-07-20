package com.rentle.domain.organization.dto;

import java.util.UUID;

public record MemberResponse(
        UUID assignmentId,
        UUID userId,
        String fullName,
        String email,
        UUID roleId,
        String roleName,
        String roleDisplayName
) {}
