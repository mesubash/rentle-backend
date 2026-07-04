package com.rentle.domain.listing.repository;

import com.rentle.domain.listing.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByIsActiveTrueOrderBySortOrderAsc();

    Optional<Category> findBySlug(String slug);
}
