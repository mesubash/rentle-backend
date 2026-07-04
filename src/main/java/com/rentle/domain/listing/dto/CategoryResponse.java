package com.rentle.domain.listing.dto;

import com.rentle.domain.listing.model.Category;

import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        String slug,
        String listingType,
        String iconName,
        int sortOrder,
        List<CategoryResponse> children
) {
    public static CategoryResponse from(Category c) {
        return from(c, List.of());
    }

    public static CategoryResponse from(Category c, List<CategoryResponse> children) {
        return new CategoryResponse(
                c.getId(),
                c.getParent() != null ? c.getParent().getId() : null,
                c.getName(),
                c.getSlug(),
                c.getListingType().name(),
                c.getIconName(),
                c.getSortOrder(),
                children
        );
    }
}
