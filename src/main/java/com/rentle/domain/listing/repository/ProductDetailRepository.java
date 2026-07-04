package com.rentle.domain.listing.repository;

import com.rentle.domain.listing.model.ProductDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductDetailRepository extends JpaRepository<ProductDetail, UUID> {

    Optional<ProductDetail> findByListingId(UUID listingId);
}
