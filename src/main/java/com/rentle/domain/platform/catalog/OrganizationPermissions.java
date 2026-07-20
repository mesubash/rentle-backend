package com.rentle.domain.platform.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

/** Org-scoped permissions. Held via an assignment at an organization's scope, never at ROOT. */
@Component
public class OrganizationPermissions implements PermissionCatalog {

    @Override
    public List<PermissionDefinition> permissions() {
        return List.of(
                new PermissionDefinition(PermissionKeys.ORGANIZATION_ORG_MANAGE, "organization", "org", "manage", "Edit the organization profile"),
                new PermissionDefinition(PermissionKeys.ORGANIZATION_MEMBER_MANAGE, "organization", "member", "manage", "Invite and remove organization members"),
                new PermissionDefinition(PermissionKeys.ORGANIZATION_WORKER_MANAGE, "organization", "worker", "manage", "Manage the organization worker registry"),
                new PermissionDefinition(PermissionKeys.ORGANIZATION_LISTING_MANAGE, "organization", "listing", "manage", "Create and manage listings as the organization"),
                new PermissionDefinition(PermissionKeys.ORGANIZATION_BOOKING_MANAGE, "organization", "booking", "manage", "Manage the organization's bookings and assign workers")
        );
    }
}
