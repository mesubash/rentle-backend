package com.rentle.domain.platform.repository;

import com.rentle.domain.platform.model.Scope;
import com.rentle.domain.platform.model.ScopeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScopeRepository extends JpaRepository<Scope, UUID> {
    Optional<Scope> findFirstByType(ScopeType type);
}
