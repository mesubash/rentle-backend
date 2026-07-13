package com.rentle.domain.platform.dto;

import com.rentle.domain.platform.model.Role;

import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String displayName,
        String description,
        boolean systemRole,
        Set<String> permissionKeys
) {
    public static RoleResponse from(Role role, Set<String> permissionKeys) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDisplayName(),
                role.getDescription(),
                Boolean.TRUE.equals(role.getIsSystemRole()),
                Set.copyOf(permissionKeys)
        );
    }
}
