package com.rentle.domain.organization.dto;

import com.rentle.domain.organization.model.Organization;

import java.util.List;
import java.util.UUID;

/** Full org view for a member. {@code myPermissions} are the org-scoped permission keys the
 *  requesting user holds here, so the UI can gate actions the same way the backend does. */
public record OrgResponse(
        UUID id,
        String name,
        String slug,
        String bio,
        String logoUrl,
        List<String> myPermissions
) {
    public static OrgResponse from(Organization org, List<String> myPermissions) {
        return new OrgResponse(org.getId(), org.getName(), org.getSlug(), org.getBio(), org.getLogoUrl(), myPermissions);
    }
}
