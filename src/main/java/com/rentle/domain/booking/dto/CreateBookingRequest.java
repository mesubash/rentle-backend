package com.rentle.domain.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID listingId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        @Size(max = 500) String note
) {}
