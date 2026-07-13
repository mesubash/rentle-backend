package com.rentle.domain.platform.catalog;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class RoleSeeds {

    private final Map<String, RoleSeed> roles;

    public RoleSeeds() {
        Set<String> allKeys = Set.of(
                PermissionKeys.PLATFORM_ROLE_READ,
                PermissionKeys.PLATFORM_ROLE_MANAGE,
                PermissionKeys.PLATFORM_PERMISSION_READ,
                PermissionKeys.PLATFORM_PERMISSION_MANAGE,
                PermissionKeys.PLATFORM_ASSIGNMENT_READ,
                PermissionKeys.PLATFORM_ASSIGNMENT_MANAGE,
                PermissionKeys.PLATFORM_SCOPE_READ,
                PermissionKeys.PLATFORM_SCOPE_MANAGE,
                PermissionKeys.IDENTITY_USER_READ,
                PermissionKeys.IDENTITY_USER_SUSPEND,
                PermissionKeys.KYC_SUBMISSION_READ,
                PermissionKeys.KYC_SUBMISSION_APPROVE,
                PermissionKeys.KYC_SUBMISSION_REJECT,
                PermissionKeys.LISTING_LISTING_READ,
                PermissionKeys.LISTING_LISTING_MODERATE,
                PermissionKeys.LISTING_CATEGORY_MANAGE,
                PermissionKeys.BOOKING_BOOKING_READ
        );

        Map<String, RoleSeed> definitions = new LinkedHashMap<>();
        definitions.put("SUPER_ADMIN", new RoleSeed("Super Admin", null, true, allKeys));
        definitions.put("ADMIN", new RoleSeed("Admin", "Day-to-day marketplace operations", false, Set.of(
                PermissionKeys.IDENTITY_USER_READ,
                PermissionKeys.IDENTITY_USER_SUSPEND,
                PermissionKeys.KYC_SUBMISSION_READ,
                PermissionKeys.KYC_SUBMISSION_APPROVE,
                PermissionKeys.KYC_SUBMISSION_REJECT,
                PermissionKeys.LISTING_LISTING_READ,
                PermissionKeys.LISTING_LISTING_MODERATE,
                PermissionKeys.LISTING_CATEGORY_MANAGE,
                PermissionKeys.BOOKING_BOOKING_READ
        )));
        definitions.put("KYC_REVIEWER", new RoleSeed("KYC Reviewer", null, false, Set.of(
                PermissionKeys.KYC_SUBMISSION_READ,
                PermissionKeys.KYC_SUBMISSION_APPROVE,
                PermissionKeys.KYC_SUBMISSION_REJECT
        )));
        definitions.put("SUPPORT", new RoleSeed("Support", "Read-only support access", false, Set.of(
                PermissionKeys.IDENTITY_USER_READ,
                PermissionKeys.BOOKING_BOOKING_READ,
                PermissionKeys.LISTING_LISTING_READ
        )));
        definitions.put("USER", new RoleSeed("User", "Marketplace user", true, Set.of()));
        this.roles = Map.copyOf(definitions);
    }

    public Map<String, RoleSeed> roles() {
        return roles;
    }

    public record RoleSeed(
            String displayName,
            String description,
            boolean systemRole,
            Set<String> permissionKeys
    ) {
        public RoleSeed {
            permissionKeys = Set.copyOf(new LinkedHashSet<>(permissionKeys));
        }
    }
}
