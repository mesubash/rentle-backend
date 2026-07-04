package com.rentle.domain.listing.dto;

import com.rentle.domain.listing.model.ListingStatus;
import com.rentle.domain.listing.model.PriceUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** All fields optional — only non-null values are applied. */
public record UpdateListingRequest(
        @Size(min = 5, max = 120) String title,
        @Size(min = 20, max = 2000) String description,
        @DecimalMin("1.0") BigDecimal pricePerUnit,
        PriceUnit priceUnit,
        @Size(max = 50) String district,
        @Size(max = 200) String locationText,
        @DecimalMin("0.0") BigDecimal depositAmount,
        ListingStatus status,
        @Valid ProductDetailDto product,
        @Valid ServiceDetailDto service
) {}
