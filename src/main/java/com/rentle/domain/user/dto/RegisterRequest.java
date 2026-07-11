package com.rentle.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Email-first signup — phone is added and verified later, before transacting. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 100)
        String email,

        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password,

        @NotBlank @Size(min = 2, max = 100)
        String fullName
) {}
