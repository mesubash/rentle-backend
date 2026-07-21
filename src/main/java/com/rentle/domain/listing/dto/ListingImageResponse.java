package com.rentle.domain.listing.dto;

import com.rentle.domain.listing.model.ListingImage;

import java.util.UUID;

public record ListingImageResponse(UUID id, String url, int sortOrder) {

    public static ListingImageResponse from(ListingImage image) {
        return new ListingImageResponse(image.getId(), image.getUrl(), image.getSortOrder());
    }
}
