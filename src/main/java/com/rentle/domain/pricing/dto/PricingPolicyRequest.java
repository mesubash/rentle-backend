package com.rentle.domain.pricing.dto;

import com.rentle.domain.pricing.model.CancellationTier;
import com.rentle.domain.pricing.model.DepositBand;

import java.util.List;

public record PricingPolicyRequest(
        List<DepositBand> depositBands,
        List<CancellationTier> cancellationTiers
) {}
