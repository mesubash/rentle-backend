package com.rentle.domain.platform.dto;

import com.rentle.domain.platform.model.Permission;

import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String key,
        String domain,
        String resource,
        String action,
        String description,
        boolean deprecated
) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getKey(),
                permission.getDomain(),
                permission.getResource(),
                permission.getAction(),
                permission.getDescription(),
                Boolean.TRUE.equals(permission.getIsDeprecated())
        );
    }
}
