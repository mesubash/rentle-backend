package com.rentle.domain.listing.dto;

import com.rentle.domain.listing.model.ListingType;
import com.rentle.domain.listing.model.PriceUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateListingRequest(
        @NotBlank @Size(min = 5, max = 120) String title,
        @NotBlank @Size(min = 20, max = 2000) String description,
        @NotNull UUID categoryId,
        @NotNull ListingType type,
        @NotNull @DecimalMin("1.0") BigDecimal pricePerUnit,
        @NotNull PriceUnit priceUnit,
        @NotBlank @Size(max = 50) String district,
        @Size(max = 200) String locationText,
        @DecimalMin("0.0") BigDecimal depositAmount,
        @Size(max = 2000) String rentalTerms,
        java.util.Map<String, Object> attributes,
        /** When set, the listing is created as this organization (the caller must be a member
         *  with organization.listing.manage). When null, it is owned by the acting user. */
        UUID orgId,
        @Valid ProductDetailDto product,
        @Valid ServiceDetailDto service
) {}
