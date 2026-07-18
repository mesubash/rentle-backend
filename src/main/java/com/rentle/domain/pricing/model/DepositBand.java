package com.rentle.domain.pricing.model;

import java.math.BigDecimal;

/** Deposit guidance for a declared item-value range (docs/13). */
public record DepositBand(
        BigDecimal minValue,
        BigDecimal maxValue,
        BigDecimal depositMin,
        BigDecimal depositMax,
        BigDecimal damageCap
) {}
