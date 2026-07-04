package com.rentle.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpSendRequest(
        @NotBlank String phoneNumber
) {}
