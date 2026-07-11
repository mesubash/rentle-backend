package com.rentle.domain.user.repository;

import com.rentle.domain.user.model.KycDetail;
import com.rentle.domain.user.model.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycDetailRepository extends JpaRepository<KycDetail, UUID> {

    Optional<KycDetail> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "user")
    Page<KycDetail> findByStatusOrderByCreatedAtAsc(KycStatus status, Pageable pageable);
}
