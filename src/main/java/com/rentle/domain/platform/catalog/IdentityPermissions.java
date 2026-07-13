package com.rentle.domain.platform.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IdentityPermissions implements PermissionCatalog {

    @Override
    public List<PermissionDefinition> permissions() {
        return List.of(
                new PermissionDefinition(PermissionKeys.IDENTITY_USER_READ, "identity", "user", "read", "List and view user accounts"),
                new PermissionDefinition(PermissionKeys.IDENTITY_USER_SUSPEND, "identity", "user", "suspend", "Suspend or unsuspend an account"),
                new PermissionDefinition(PermissionKeys.IDENTITY_USER_RESET_PASSWORD, "identity", "user", "reset_password", "Set a new password for any account")
        );
    }
}
