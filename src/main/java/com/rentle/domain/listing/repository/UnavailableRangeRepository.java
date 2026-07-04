package com.rentle.domain.listing.repository;

import com.rentle.domain.listing.model.UnavailableRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UnavailableRangeRepository extends JpaRepository<UnavailableRange, UUID> {

    List<UnavailableRange> findByListingIdOrderByStartDateAsc(UUID listingId);

    @Query("""
        SELECT COUNT(r) > 0 FROM UnavailableRange r
        WHERE r.listing.id = :listingId
        AND r.startDate <= :endDate
        AND r.endDate >= :startDate
    """)
    boolean existsOverlap(@Param("listingId") UUID listingId,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate);
}
