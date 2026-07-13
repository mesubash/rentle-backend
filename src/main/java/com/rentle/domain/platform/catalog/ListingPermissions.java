package com.rentle.domain.platform.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ListingPermissions implements PermissionCatalog {

    @Override
    public List<PermissionDefinition> permissions() {
        return List.of(
                new PermissionDefinition(PermissionKeys.LISTING_LISTING_READ, "listing", "listing", "read", "View all listings"),
                new PermissionDefinition(PermissionKeys.LISTING_LISTING_MODERATE, "listing", "listing", "moderate", "Moderate any listing"),
                new PermissionDefinition(PermissionKeys.LISTING_CATEGORY_MANAGE, "listing", "category", "manage", "Manage listing categories")
        );
    }
}
