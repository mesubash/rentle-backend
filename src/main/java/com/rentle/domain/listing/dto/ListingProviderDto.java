package com.rentle.domain.listing.dto;

import com.rentle.domain.organization.model.Organization;
import com.rentle.domain.user.model.User;

import java.util.UUID;

/** Who provides a listing — an individual user or an organization. The marketplace shows both
 *  the same way, so cards and detail pages read {@code provider} regardless of owner kind. */
public record ListingProviderDto(String type, UUID id, String name, String logoUrl, String slug) {

    public static ListingProviderDto user(User u) {
        return new ListingProviderDto("USER", u.getId(), u.getFullName(), u.getProfilePhotoUrl(), null);
    }

    public static ListingProviderDto org(Organization o) {
        return new ListingProviderDto("ORG", o.getId(), o.getName(), o.getLogoUrl(), o.getSlug());
    }
}
