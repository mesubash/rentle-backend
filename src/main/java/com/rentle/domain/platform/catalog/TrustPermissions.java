package com.rentle.domain.platform.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrustPermissions implements PermissionCatalog {

    @Override
    public List<PermissionDefinition> permissions() {
        return List.of(
                new PermissionDefinition(PermissionKeys.TRUST_REPORT_READ, "trust", "report", "read", "View user reports"),
                new PermissionDefinition(PermissionKeys.TRUST_REPORT_RESOLVE, "trust", "report", "resolve", "Resolve or dismiss reports"),
                new PermissionDefinition(PermissionKeys.PLATFORM_SETTINGS_MANAGE, "platform", "settings", "manage", "Edit platform settings"),
                new PermissionDefinition(PermissionKeys.BOOKING_FEE_MANAGE, "booking", "fee", "manage", "View and invoice platform fees")
        );
    }
}
