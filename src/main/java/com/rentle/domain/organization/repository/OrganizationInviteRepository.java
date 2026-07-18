package com.rentle.domain.organization.repository;

import com.rentle.domain.organization.model.OrganizationInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, UUID> {
    Optional<OrganizationInvite> findByToken(String token);

    List<OrganizationInvite> findByOrgIdOrderByCreatedAtAsc(UUID orgId);

    Optional<OrganizationInvite> findByOrgIdAndEmailIgnoreCase(UUID orgId, String email);
}
