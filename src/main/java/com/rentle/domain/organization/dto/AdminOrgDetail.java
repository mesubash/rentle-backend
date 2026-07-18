package com.rentle.domain.organization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full org record for the admin console: profile, members and headline counts. */
public record AdminOrgDetail(
        UUID id,
        String name,
        String slug,
        String bio,
        String logoUrl,
        UUID createdBy,
        Instant createdAt,
        long listingCount,
        List<MemberResponse> members
) {}
