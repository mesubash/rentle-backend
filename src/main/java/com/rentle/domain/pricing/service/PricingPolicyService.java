package com.rentle.domain.pricing.service;

import com.rentle.domain.pricing.dto.PricingPolicyRequest;
import com.rentle.domain.pricing.model.CancellationTier;
import com.rentle.domain.pricing.model.CategoryPricingPolicy;
import com.rentle.domain.pricing.model.DepositBand;
import com.rentle.domain.pricing.repository.CategoryPricingPolicyRepository;
import com.rentle.shared.exception.RentleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PricingPolicyService {

    private final CategoryPricingPolicyRepository repository;

    public PricingPolicyService(CategoryPricingPolicyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<CategoryPricingPolicy> find(UUID categoryId) {
        return repository.findById(categoryId);
    }

    /** The cancellation schedule for a category (empty if none configured) — snapshotted onto bookings. */
    @Transactional(readOnly = true)
    public List<CancellationTier> cancellationTiers(UUID categoryId) {
        return repository.findById(categoryId).map(CategoryPricingPolicy::getCancellationTiers).orElse(List.of());
    }

    @Transactional
    public CategoryPricingPolicy upsert(UUID categoryId, PricingPolicyRequest req, UUID adminId) {
        validate(req);
        CategoryPricingPolicy policy = repository.findById(categoryId).orElseGet(() -> {
            CategoryPricingPolicy p = new CategoryPricingPolicy();
            p.setCategoryId(categoryId);
            return p;
        });
        policy.setDepositBands(req.depositBands() != null ? req.depositBands() : List.of());
        policy.setCancellationTiers(req.cancellationTiers() != null ? req.cancellationTiers() : List.of());
        policy.setUpdatedBy(adminId);
        policy.setUpdatedAt(Instant.now());
        return repository.save(policy);
    }

    private void validate(PricingPolicyRequest req) {
        if (req.depositBands() != null) {
            for (DepositBand b : req.depositBands()) {
                if (b.minValue() == null || b.maxValue() == null || b.minValue().compareTo(b.maxValue()) > 0) {
                    throw new RentleException("Deposit band value range is invalid");
                }
            }
        }
        if (req.cancellationTiers() != null) {
            for (CancellationTier t : req.cancellationTiers()) {
                if (t.hoursBefore() < 0 || t.withholdPct() == null
                        || t.withholdPct().signum() < 0 || t.withholdPct().compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new RentleException("Cancellation tier is invalid (hours >= 0, withhold 0-100%)");
                }
            }
        }
    }
}
