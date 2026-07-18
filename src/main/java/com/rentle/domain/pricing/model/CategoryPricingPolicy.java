package com.rentle.domain.pricing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "category_pricing_policy")
@Getter
@Setter
@NoArgsConstructor
public class CategoryPricingPolicy {

    @Id
    @Column(name = "category_id")
    private UUID categoryId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deposit_bands", columnDefinition = "jsonb", nullable = false)
    private List<DepositBand> depositBands = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cancellation_tiers", columnDefinition = "jsonb", nullable = false)
    private List<CancellationTier> cancellationTiers = List.of();

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
