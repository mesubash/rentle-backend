package com.rentle.domain.booking.dto;

import jakarta.validation.constraints.Size;

/** Optional reason for reject / cancel actions. */
public record BookingActionRequest(
        @Size(max = 500) String reason
) {}
