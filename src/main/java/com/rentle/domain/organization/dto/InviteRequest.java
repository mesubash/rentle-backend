package com.rentle.domain.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InviteRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @NotNull UUID roleId
) {}
