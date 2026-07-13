package com.rentle.domain.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateRoleRequest(
        @NotBlank
        @Size(max = 60)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must use uppercase letters, numbers, and underscores")
        String name,
        @NotBlank @Size(max = 120) String displayName,
        @Size(max = 300) String description,
        @NotNull Set<@NotBlank String> permissionKeys
) {}
