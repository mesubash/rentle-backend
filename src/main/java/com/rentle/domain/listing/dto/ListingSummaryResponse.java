package com.rentle.domain.listing.dto;

import com.rentle.domain.listing.model.Listing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Compact card for search results and profile listings. */
public record ListingSummaryResponse(
        UUID id,
        String type,
        String status,
        String title,
        BigDecimal pricePerUnit,
        String priceUnit,
        BigDecimal depositAmount,
        String district,
        BigDecimal averageRating,
        int reviewCount,
        String coverImage,
        Instant createdAt
) {
    public static ListingSummaryResponse from(Listing l, String coverImage) {
        return new ListingSummaryResponse(
                l.getId(),
                l.getType().name(),
                l.getStatus().name(),
                l.getTitle(),
                l.getPricePerUnit(),
                l.getPriceUnit().name(),
                l.getDepositAmount(),
                l.getDistrict(),
                l.getAverageRating(),
                l.getReviewCount(),
                coverImage,
                l.getCreatedAt()
        );
    }
}
