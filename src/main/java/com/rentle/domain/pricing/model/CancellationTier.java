package com.rentle.domain.pricing.model;

import java.math.BigDecimal;

/** One step of a time-before-start cancellation schedule (docs/13). */
public record CancellationTier(
        int hoursBefore,        // cancelling within this many hours of start...
        BigDecimal withholdPct  // ...forfeits this % of the rental charge
) {}
