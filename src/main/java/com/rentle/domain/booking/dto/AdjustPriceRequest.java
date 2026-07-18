package com.rentle.domain.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdjustPriceRequest(
        @NotNull @DecimalMin("0.0") BigDecimal totalPrice
) {}
