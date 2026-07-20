package com.rentle.domain.organization.dto;

import java.time.Instant;
import java.util.UUID;

public record InviteResponse(
        UUID id,
        String email,
        UUID roleId,
        String roleDisplayName,
        String token,
        Instant createdAt
) {}
