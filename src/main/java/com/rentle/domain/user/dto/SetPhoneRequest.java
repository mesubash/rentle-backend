package com.rentle.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetPhoneRequest(
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number")
        String phoneNumber
) {}
