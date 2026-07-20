package com.rentle.domain.platform.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PlatformPermissions implements PermissionCatalog {

    @Override
    public List<PermissionDefinition> permissions() {
        return List.of(
                permission(PermissionKeys.PLATFORM_ROLE_READ, "role", "read", "View roles and their permissions"),
                permission(PermissionKeys.PLATFORM_ROLE_MANAGE, "role", "manage", "Create, edit, and delete roles"),
                permission(PermissionKeys.PLATFORM_PERMISSION_READ, "permission", "read", "View the permission catalog"),
                permission(PermissionKeys.PLATFORM_PERMISSION_MANAGE, "permission", "manage", "Register or deprecate permissions"),
                permission(PermissionKeys.PLATFORM_ASSIGNMENT_READ, "assignment", "read", "View role assignments"),
                permission(PermissionKeys.PLATFORM_ASSIGNMENT_MANAGE, "assignment", "manage", "Grant and revoke role assignments"),
                permission(PermissionKeys.PLATFORM_SCOPE_READ, "scope", "read", "View the scope tree"),
                permission(PermissionKeys.PLATFORM_SCOPE_MANAGE, "scope", "manage", "Create and manage scopes"),
                permission(PermissionKeys.PLATFORM_ORGANIZATION_READ, "organization", "read", "View all organizations")
        );
    }

    private PermissionDefinition permission(String key, String resource, String action, String description) {
        return new PermissionDefinition(key, "platform", resource, action, description);
    }
}
