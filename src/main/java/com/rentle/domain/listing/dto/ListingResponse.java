package com.rentle.domain.listing.dto;

import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.ListingImage;
import com.rentle.domain.user.dto.PublicProfileResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ListingResponse(
        UUID id,
        PublicProfileResponse owner,
        UUID categoryId,
        String categoryName,
        String type,
        String status,
        String title,
        String description,
        BigDecimal pricePerUnit,
        String priceUnit,
        BigDecimal depositAmount,
        String district,
        String locationText,
        String rentalTerms,
        java.util.Map<String, Object> attributes,
        BigDecimal averageRating,
        int reviewCount,
        int totalBookings,
        List<String> images,
        ProductDetailDto product,
        ServiceDetailDto service,
        Instant createdAt
) {
    public static ListingResponse from(Listing l,
                                       List<ListingImage> images,
                                       ProductDetailDto product,
                                       ServiceDetailDto service) {
        return new ListingResponse(
                l.getId(),
                PublicProfileResponse.from(l.getOwner()),
                l.getCategory().getId(),
                l.getCategory().getName(),
                l.getType().name(),
                l.getStatus().name(),
                l.getTitle(),
                l.getDescription(),
                l.getPricePerUnit(),
                l.getPriceUnit().name(),
                l.getDepositAmount(),
                l.getDistrict(),
                l.getLocationText(),
                l.getRentalTerms(),
                l.getAttributes(),
                l.getAverageRating(),
                l.getReviewCount(),
                l.getTotalBookings(),
                images.stream().map(ListingImage::getUrl).toList(),
                product,
                service,
                l.getCreatedAt()
        );
    }
}
