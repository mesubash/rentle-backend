package com.rentle.domain.booking.repository;

import com.rentle.domain.booking.model.Booking;
import com.rentle.domain.booking.model.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * Application-level pre-check for a *confirmed* hold; the GiST exclusion constraint is
     * the final guard. Only APPROVED and later statuses occupy dates — a REQUESTED booking
     * does not, so multiple pending requests for the same dates may coexist (P1-27).
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.listing.id = :listingId
        AND b.status NOT IN (com.rentle.domain.booking.model.BookingStatus.CANCELLED,
                             com.rentle.domain.booking.model.BookingStatus.REJECTED,
                             com.rentle.domain.booking.model.BookingStatus.REQUESTED)
        AND b.startDate <= :endDate
        AND b.endDate >= :startDate
    """)
    boolean existsOverlap(@Param("listingId") UUID listingId,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate);

    long countByRenterIdAndStatus(UUID renterId, com.rentle.domain.booking.model.BookingStatus status);

    Page<Booking> findByStatusAndFeeInvoiced(
            com.rentle.domain.booking.model.BookingStatus status, boolean feeInvoiced, Pageable pageable);

    // Fetch the associations the list response reads, avoiding an N+1 per row.
    @EntityGraph(attributePaths = {"listing", "listing.owner", "renter"})
    @Query("SELECT b FROM Booking b WHERE b.renter.id = :userId ORDER BY b.createdAt DESC")
    Page<Booking> findByRenter(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"listing", "listing.owner", "renter"})
    @Query("SELECT b FROM Booking b WHERE b.listing.owner.id = :ownerId ORDER BY b.createdAt DESC")
    Page<Booking> findByOwner(@Param("ownerId") UUID ownerId, Pageable pageable);

    List<Booking> findByListingIdAndStatusNotInAndEndDateGreaterThanEqual(
            UUID listingId, Collection<BookingStatus> excludedStatuses, LocalDate from);
}
