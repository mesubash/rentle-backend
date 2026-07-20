package com.rentle.domain.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrgRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String bio,
        @Size(max = 500) String logoUrl
) {}
