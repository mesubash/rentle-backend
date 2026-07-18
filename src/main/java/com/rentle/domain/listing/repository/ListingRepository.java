package com.rentle.domain.listing.repository;

import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    long countByCategoryId(UUID categoryId);

    /** Hide all of an owner's live listings — used when the owner is suspended. */
    @Modifying(clearAutomatically = true)
    @Query("update Listing l set l.status = com.rentle.domain.listing.model.ListingStatus.INACTIVE "
            + "where l.owner.id = :ownerId and l.status = com.rentle.domain.listing.model.ListingStatus.ACTIVE")
    int deactivateActiveByOwner(@Param("ownerId") UUID ownerId);

    Optional<Listing> findByIdAndStatus(UUID id, ListingStatus status);

    Page<Listing> findByOwnerIdAndStatusNot(UUID ownerId, ListingStatus status, Pageable pageable);

    Page<Listing> findByOwnerIdAndStatus(UUID ownerId, ListingStatus status, Pageable pageable);

    Page<Listing> findByOrgIdAndStatusNot(UUID orgId, ListingStatus status, Pageable pageable);

    long countByOrgIdAndStatusNot(UUID orgId, ListingStatus status);

    /**
     * Search over ACTIVE listings: PostgreSQL FTS for keyword, plain filters
     * for the rest. All params passed as text and cast to keep JDBC type
     * inference happy with nullable parameters.
     */
    @Query(value = """
            SELECT l.* FROM listings l
            WHERE l.status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM categories c WHERE c.id = l.category_id AND c.is_active = true)
              AND (CAST(:type AS text) IS NULL OR l.type = CAST(:type AS text))
              AND (CAST(:categoryId AS text) IS NULL OR l.category_id = CAST(CAST(:categoryId AS text) AS uuid))
              AND (CAST(:district AS text) IS NULL OR l.district ILIKE CAST(:district AS text))
              AND (CAST(:minPrice AS numeric) IS NULL OR l.price_per_unit >= CAST(:minPrice AS numeric))
              AND (CAST(:maxPrice AS numeric) IS NULL OR l.price_per_unit <= CAST(:maxPrice AS numeric))
              AND (CAST(:q AS text) IS NULL OR l.search_vector @@ plainto_tsquery('english', CAST(:q AS text)))
            ORDER BY
              CASE WHEN CAST(:sort AS text) = 'price_asc'  THEN l.price_per_unit END ASC NULLS LAST,
              CASE WHEN CAST(:sort AS text) = 'price_desc' THEN l.price_per_unit END DESC NULLS LAST,
              CASE WHEN CAST(:sort AS text) = 'rating'     THEN l.average_rating END DESC NULLS LAST,
              l.created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM listings l
            WHERE l.status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM categories c WHERE c.id = l.category_id AND c.is_active = true)
              AND (CAST(:type AS text) IS NULL OR l.type = CAST(:type AS text))
              AND (CAST(:categoryId AS text) IS NULL OR l.category_id = CAST(CAST(:categoryId AS text) AS uuid))
              AND (CAST(:district AS text) IS NULL OR l.district ILIKE CAST(:district AS text))
              AND (CAST(:minPrice AS numeric) IS NULL OR l.price_per_unit >= CAST(:minPrice AS numeric))
              AND (CAST(:maxPrice AS numeric) IS NULL OR l.price_per_unit <= CAST(:maxPrice AS numeric))
              AND (CAST(:q AS text) IS NULL OR l.search_vector @@ plainto_tsquery('english', CAST(:q AS text)))
            """,
            nativeQuery = true)
    Page<Listing> search(@Param("q") String q,
                         @Param("type") String type,
                         @Param("categoryId") String categoryId,
                         @Param("district") String district,
                         @Param("minPrice") java.math.BigDecimal minPrice,
                         @Param("maxPrice") java.math.BigDecimal maxPrice,
                         @Param("sort") String sort,
                         Pageable pageable);
}
