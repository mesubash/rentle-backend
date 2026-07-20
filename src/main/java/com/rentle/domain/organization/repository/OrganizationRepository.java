package com.rentle.domain.organization.repository;

import com.rentle.domain.organization.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    boolean existsBySlug(String slug);

    Optional<Organization> findBySlug(String slug);

    List<Organization> findByScopeIdInOrderByNameAsc(List<UUID> scopeIds);

    Page<Organization> findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String name, Pageable pageable);

    Page<Organization> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
