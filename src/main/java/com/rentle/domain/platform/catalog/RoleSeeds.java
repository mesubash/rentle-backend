package com.rentle.domain.platform.catalog;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class RoleSeeds {

    /** Default org membership roles. Seeded once, then admin-editable (non-system). */
    public static final String ORG_OWNER = "ORG_OWNER";
    public static final String ORG_ADMIN = "ORG_ADMIN";
    public static final String ORG_STAFF = "ORG_STAFF";

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
                PermissionKeys.IDENTITY_USER_RESET_PASSWORD,
                PermissionKeys.KYC_SUBMISSION_READ,
                PermissionKeys.KYC_SUBMISSION_APPROVE,
                PermissionKeys.KYC_SUBMISSION_REJECT,
                PermissionKeys.LISTING_LISTING_READ,
                PermissionKeys.LISTING_LISTING_MODERATE,
                PermissionKeys.LISTING_CATEGORY_MANAGE,
                PermissionKeys.BOOKING_BOOKING_READ,
                PermissionKeys.TRUST_REPORT_READ,
                PermissionKeys.TRUST_REPORT_RESOLVE,
                PermissionKeys.PLATFORM_SETTINGS_MANAGE,
                PermissionKeys.BOOKING_FEE_MANAGE
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
                PermissionKeys.BOOKING_BOOKING_READ,
                PermissionKeys.TRUST_REPORT_READ,
                PermissionKeys.TRUST_REPORT_RESOLVE,
                PermissionKeys.PLATFORM_SETTINGS_MANAGE,
                PermissionKeys.BOOKING_FEE_MANAGE
        )));
        definitions.put("KYC_REVIEWER", new RoleSeed("KYC Reviewer", null, false, Set.of(
                PermissionKeys.KYC_SUBMISSION_READ,
                PermissionKeys.KYC_SUBMISSION_APPROVE,
                PermissionKeys.KYC_SUBMISSION_REJECT
        )));
        definitions.put("SUPPORT", new RoleSeed("Support", "Read-only support access", false, Set.of(
                PermissionKeys.IDENTITY_USER_READ,
                PermissionKeys.BOOKING_BOOKING_READ,
                PermissionKeys.LISTING_LISTING_READ,
                PermissionKeys.TRUST_REPORT_READ
        )));
        definitions.put("USER", new RoleSeed("User", "Marketplace user", true, Set.of()));

        // Org membership roles — non-system so an admin can retune their permissions at runtime.
        definitions.put(ORG_OWNER, new RoleSeed("Organization Owner", "Full control of the organization", false, Set.of(
                PermissionKeys.ORGANIZATION_ORG_MANAGE,
                PermissionKeys.ORGANIZATION_MEMBER_MANAGE,
                PermissionKeys.ORGANIZATION_WORKER_MANAGE,
                PermissionKeys.ORGANIZATION_LISTING_MANAGE,
                PermissionKeys.ORGANIZATION_BOOKING_MANAGE
        )));
        definitions.put(ORG_ADMIN, new RoleSeed("Organization Admin", "Manage members, listings and bookings", false, Set.of(
                PermissionKeys.ORGANIZATION_MEMBER_MANAGE,
                PermissionKeys.ORGANIZATION_WORKER_MANAGE,
                PermissionKeys.ORGANIZATION_LISTING_MANAGE,
                PermissionKeys.ORGANIZATION_BOOKING_MANAGE
        )));
        definitions.put(ORG_STAFF, new RoleSeed("Organization Staff", "Manage listings, workers and bookings", false, Set.of(
                PermissionKeys.ORGANIZATION_WORKER_MANAGE,
                PermissionKeys.ORGANIZATION_LISTING_MANAGE,
                PermissionKeys.ORGANIZATION_BOOKING_MANAGE
        )));

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
