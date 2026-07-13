package com.rentle.domain.platform.repository;

import com.rentle.domain.platform.model.RolePermission;
import com.rentle.domain.platform.model.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    List<RolePermission> findByIdRoleId(UUID roleId);

    void deleteByIdRoleId(UUID roleId);
}
