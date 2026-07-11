package com.rentle.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

/** One-time handoff code swapped for a session after the Google redirect. */
public record GoogleExchangeRequest(
        @NotBlank String code
) {}
