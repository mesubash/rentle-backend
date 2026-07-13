package com.rentle.domain.platform.catalog;

public record PermissionDefinition(
        String key,
        String domain,
        String resource,
        String action,
        String description
) {}
