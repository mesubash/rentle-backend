package com.rentle.domain.pricing.dto;

import com.rentle.domain.pricing.model.CancellationTier;
import com.rentle.domain.pricing.model.CategoryPricingPolicy;
import com.rentle.domain.pricing.model.DepositBand;

import java.util.List;
import java.util.UUID;

public record PricingPolicyResponse(
        UUID categoryId,
        List<DepositBand> depositBands,
        List<CancellationTier> cancellationTiers
) {
    public static PricingPolicyResponse from(CategoryPricingPolicy p) {
        return new PricingPolicyResponse(p.getCategoryId(), p.getDepositBands(), p.getCancellationTiers());
    }

    public static PricingPolicyResponse empty(UUID categoryId) {
        return new PricingPolicyResponse(categoryId, List.of(), List.of());
    }
}
