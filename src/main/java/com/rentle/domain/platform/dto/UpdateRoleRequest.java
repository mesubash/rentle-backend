package com.rentle.domain.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateRoleRequest(
        @NotBlank @Size(max = 120) String displayName,
        @Size(max = 300) String description,
        @NotNull Set<@NotBlank String> permissionKeys
) {}
