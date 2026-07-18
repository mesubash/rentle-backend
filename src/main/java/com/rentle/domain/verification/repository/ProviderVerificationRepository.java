package com.rentle.domain.verification.repository;

import com.rentle.domain.verification.model.ProviderVerification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderVerificationRepository extends JpaRepository<ProviderVerification, UUID> {

    Optional<ProviderVerification> findByUserIdAndCategoryId(UUID userId, UUID categoryId);

    List<ProviderVerification> findByUserId(UUID userId);

    Page<ProviderVerification> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    boolean existsByUserIdAndCategoryIdAndStatus(UUID userId, UUID categoryId, String status);
}
