package com.rentle.domain.platform.repository;

import com.rentle.domain.platform.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByKey(String key);
}
