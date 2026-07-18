package com.rentle.domain.organization.dto;

import java.time.Instant;
import java.util.UUID;

/** One organization in the admin companies lookup. */
public record AdminOrgRow(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        long memberCount,
        long listingCount,
        Instant createdAt
) {}
