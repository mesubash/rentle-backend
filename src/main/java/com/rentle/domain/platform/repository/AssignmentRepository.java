package com.rentle.domain.platform.repository;

import com.rentle.domain.platform.model.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    @Query("""
            SELECT p.key FROM Assignment a
            JOIN RolePermission rp ON rp.id.roleId = a.role.id
            JOIN Permission p ON p.id = rp.id.permissionId
            WHERE a.subject.id = :userId AND a.revokedAt IS NULL AND p.isDeprecated = false
            """)
    Set<String> findLivePermissionKeys(@Param("userId") UUID userId);

    List<Assignment> findBySubjectIdAndRevokedAtIsNull(UUID subjectId);

    List<Assignment> findByRoleIdAndRevokedAtIsNull(UUID roleId);

    boolean existsByRoleIdAndRevokedAtIsNull(UUID roleId);

    boolean existsBySubjectIdAndRoleIdAndScopeIdAndRevokedAtIsNull(UUID subjectId, UUID roleId, UUID scopeId);

    long countByRoleIdAndRevokedAtIsNull(UUID roleId);

    long countBySubjectIdAndRoleIdAndRevokedAtIsNull(UUID subjectId, UUID roleId);

    @Query("""
            SELECT a FROM Assignment a
            JOIN FETCH a.subject
            JOIN FETCH a.role
            JOIN FETCH a.scope
            LEFT JOIN FETCH a.grantedBy
            WHERE a.revokedAt IS NULL
              AND (:userId IS NULL OR a.subject.id = :userId)
              AND (:roleId IS NULL OR a.role.id = :roleId)
            ORDER BY a.createdAt DESC
            """)
    List<Assignment> findLiveAssignments(@Param("userId") UUID userId, @Param("roleId") UUID roleId);
}
