package com.rentle.domain.listing.dto;

import com.rentle.domain.listing.model.Category;

import java.util.UUID;

/**
 * Admin view of a category — includes hidden ones and their active flag and
 * listing count, so an operator can decide what to launch or pause (docs/12).
 */
public record AdminCategoryRow(
        UUID id,
        String name,
        String slug,
        String listingType,
        String iconName,
        int sortOrder,
        boolean active,
        long listingCount
) {
    public static AdminCategoryRow from(Category c, long listingCount) {
        return new AdminCategoryRow(
                c.getId(),
                c.getName(),
                c.getSlug(),
                c.getListingType().name(),
                c.getIconName(),
                c.getSortOrder(),
                Boolean.TRUE.equals(c.getIsActive()),
                listingCount
        );
    }
}
