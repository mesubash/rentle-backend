package com.rentle.domain.listing.repository;

import com.rentle.domain.listing.model.ListingImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ListingImageRepository extends JpaRepository<ListingImage, UUID> {

    List<ListingImage> findByListingIdOrderBySortOrderAsc(UUID listingId);

    long countByListingId(UUID listingId);

    List<ListingImage> findByListingIdInOrderBySortOrderAsc(Collection<UUID> listingIds);
}
