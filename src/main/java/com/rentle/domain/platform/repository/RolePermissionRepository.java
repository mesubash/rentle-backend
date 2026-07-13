package com.rentle.domain.platform.repository;

import com.rentle.domain.platform.model.RolePermission;
import com.rentle.domain.platform.model.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    @Query("SELECT rp FROM RolePermission rp JOIN FETCH rp.permission WHERE rp.id.roleId = :roleId")
    List<RolePermission> findByRoleIdWithPermission(@Param("roleId") UUID roleId);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.id.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") UUID roleId);
}
