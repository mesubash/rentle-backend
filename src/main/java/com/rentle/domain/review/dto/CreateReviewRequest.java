package com.rentle.domain.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateReviewRequest(
        @NotNull UUID bookingId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String comment
) {}
