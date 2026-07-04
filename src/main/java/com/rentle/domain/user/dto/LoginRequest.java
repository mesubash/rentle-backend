package com.rentle.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String identifier,   // phone number or email
        @NotBlank String password
) {}
