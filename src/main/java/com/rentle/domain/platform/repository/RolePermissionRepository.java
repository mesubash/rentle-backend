package com.rentle.domain.platform.repository;

import com.rentle.domain.platform.model.RolePermission;
import com.rentle.domain.platform.model.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
}
