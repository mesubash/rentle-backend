package com.rentle.domain.listing.repository;

import com.rentle.domain.listing.model.ServiceDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServiceDetailRepository extends JpaRepository<ServiceDetail, UUID> {

    Optional<ServiceDetail> findByListingId(UUID listingId);
}
