package com.rentle.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CodeRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Code must be 6 digits")
        String code
) {}
