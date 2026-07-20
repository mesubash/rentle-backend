package com.rentle.domain.pricing.repository;

import com.rentle.domain.pricing.model.CategoryPricingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoryPricingPolicyRepository extends JpaRepository<CategoryPricingPolicy, UUID> {
}
