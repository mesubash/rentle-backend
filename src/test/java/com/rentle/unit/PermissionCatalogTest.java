package com.rentle.unit;

import com.rentle.domain.platform.catalog.BookingPermissions;
import com.rentle.domain.platform.catalog.IdentityPermissions;
import com.rentle.domain.platform.catalog.KycPermissions;
import com.rentle.domain.platform.catalog.ListingPermissions;
import com.rentle.domain.platform.catalog.OrganizationPermissions;
import com.rentle.domain.platform.catalog.PermissionCatalog;
import com.rentle.domain.platform.catalog.PermissionDefinition;
import com.rentle.domain.platform.catalog.PlatformPermissions;
import com.rentle.domain.platform.catalog.RoleSeeds;
import com.rentle.domain.platform.catalog.TrustPermissions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCatalogTest {

    private static final Pattern KEY_FORMAT = Pattern.compile("^[a-z_]+\\.[a-z_]+\\.[a-z_]+$");

    private final List<PermissionCatalog> catalogs = List.of(
            new PlatformPermissions(),
            new IdentityPermissions(),
            new KycPermissions(),
            new ListingPermissions(),
            new BookingPermissions(),
            new TrustPermissions(),
            new OrganizationPermissions()
    );

    @Test
    void catalogKeysAreValidUniqueAndCoverRoleSeeds() {
        List<PermissionDefinition> definitions = catalogs.stream()
                .flatMap(catalog -> catalog.permissions().stream())
                .toList();

        assertTrue(definitions.stream().allMatch(definition -> KEY_FORMAT.matcher(definition.key()).matches()));

        Set<String> keys = definitions.stream()
                .map(PermissionDefinition::key)
                .collect(Collectors.toSet());
        assertEquals(definitions.size(), keys.size(), "Permission catalog keys must be unique");

        RoleSeeds roleSeeds = new RoleSeeds();
        assertTrue(roleSeeds.roles().values().stream()
                .flatMap(role -> role.permissionKeys().stream())
                .allMatch(keys::contains));
    }
}
