package com.rentle.domain.platform.repository;

import com.rentle.domain.platform.model.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    /** Global (platform) permission keys — ROOT-scoped assignments only. Org-scoped assignments
     *  are resolved per-org via {@link #findLivePermissionKeysInScope} and never leak here. */
    @Query("""
            SELECT p.key FROM Assignment a
            JOIN RolePermission rp ON rp.id.roleId = a.role.id
            JOIN Permission p ON p.id = rp.id.permissionId
            WHERE a.subject.id = :userId AND a.revokedAt IS NULL AND p.isDeprecated = false
              AND a.scope.type = com.rentle.domain.platform.model.ScopeType.ROOT
            """)
    Set<String> findLivePermissionKeys(@Param("userId") UUID userId);

    /** Live permission keys a user holds within one scope only (e.g. an organization). */
    @Query("""
            SELECT p.key FROM Assignment a
            JOIN RolePermission rp ON rp.id.roleId = a.role.id
            JOIN Permission p ON p.id = rp.id.permissionId
            WHERE a.subject.id = :userId AND a.scope.id = :scopeId
              AND a.revokedAt IS NULL AND p.isDeprecated = false
            """)
    Set<String> findLivePermissionKeysInScope(@Param("userId") UUID userId, @Param("scopeId") UUID scopeId);

    /** Scope ids of the organizations a user is a live member of. */
    @Query("""
            SELECT DISTINCT a.scope.id FROM Assignment a
            WHERE a.subject.id = :userId AND a.revokedAt IS NULL
              AND a.scope.type = com.rentle.domain.platform.model.ScopeType.ORG
            """)
    List<UUID> findLiveOrgScopeIds(@Param("userId") UUID userId);

    @Query("""
            SELECT a FROM Assignment a
            JOIN FETCH a.subject
            JOIN FETCH a.role
            WHERE a.scope.id = :scopeId AND a.revokedAt IS NULL
            ORDER BY a.createdAt ASC
            """)
    List<Assignment> findMembers(@Param("scopeId") UUID scopeId);

    List<Assignment> findBySubjectIdAndScopeIdAndRevokedAtIsNull(UUID subjectId, UUID scopeId);

    boolean existsBySubjectIdAndScopeIdAndRevokedAtIsNull(UUID subjectId, UUID scopeId);

    long countByScopeIdAndRoleIdAndRevokedAtIsNull(UUID scopeId, UUID roleId);

    long countByScopeIdAndRevokedAtIsNull(UUID scopeId);

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
              AND a.scope.type = com.rentle.domain.platform.model.ScopeType.ROOT
              AND (:userId IS NULL OR a.subject.id = :userId)
              AND (:roleId IS NULL OR a.role.id = :roleId)
            ORDER BY a.createdAt DESC
            """)
    List<Assignment> findLiveAssignments(@Param("userId") UUID userId, @Param("roleId") UUID roleId);
}
