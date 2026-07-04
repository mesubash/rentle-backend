package com.rentle.domain.listing.dto;

import com.rentle.domain.listing.model.ItemCondition;
import com.rentle.domain.listing.model.ProductDetail;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductDetailDto(
        @NotNull ItemCondition condition,
        @Size(max = 100) String brand,
        @Size(max = 100) String model,
        @Min(1) Integer minRentalDays,
        @Min(1) Integer maxRentalDays
) {
    public static ProductDetailDto from(ProductDetail d) {
        return new ProductDetailDto(d.getCondition(), d.getBrand(), d.getModel(),
                d.getMinRentalDays(), d.getMaxRentalDays());
    }
}
