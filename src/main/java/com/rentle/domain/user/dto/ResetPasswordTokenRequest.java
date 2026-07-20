package com.rentle.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordTokenRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password
) {}
