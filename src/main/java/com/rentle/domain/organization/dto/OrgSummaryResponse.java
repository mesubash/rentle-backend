package com.rentle.domain.organization.dto;

import com.rentle.domain.organization.model.Organization;

import java.util.UUID;

/** Compact org identity for the account switcher and marketplace provider display. */
public record OrgSummaryResponse(UUID id, String name, String slug, String logoUrl) {
    public static OrgSummaryResponse from(Organization org) {
        return new OrgSummaryResponse(org.getId(), org.getName(), org.getSlug(), org.getLogoUrl());
    }
}
