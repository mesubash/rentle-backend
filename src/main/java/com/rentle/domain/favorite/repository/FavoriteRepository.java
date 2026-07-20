package com.rentle.domain.favorite.repository;

import com.rentle.domain.favorite.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    Optional<Favorite> findByUserIdAndListingId(UUID userId, UUID listingId);

    List<Favorite> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT f.listingId FROM Favorite f WHERE f.userId = :userId")
    List<UUID> listingIds(@Param("userId") UUID userId);
}
