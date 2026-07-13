package com.rentle.domain.platform.service;

import com.rentle.domain.platform.dto.AssignmentResponse;
import com.rentle.domain.platform.dto.CreateAssignmentRequest;
import com.rentle.domain.platform.dto.CreateRoleRequest;
import com.rentle.domain.platform.dto.PermissionResponse;
import com.rentle.domain.platform.dto.RoleResponse;
import com.rentle.domain.platform.dto.UpdateRoleRequest;
import com.rentle.domain.platform.dto.UserLookupResponse;
import com.rentle.domain.platform.model.Assignment;
import com.rentle.domain.platform.model.Permission;
import com.rentle.domain.platform.model.Role;
import com.rentle.domain.platform.model.RolePermission;
import com.rentle.domain.platform.model.Scope;
import com.rentle.domain.platform.model.ScopeType;
import com.rentle.domain.platform.repository.AssignmentRepository;
import com.rentle.domain.platform.repository.PermissionRepository;
import com.rentle.domain.platform.repository.RolePermissionRepository;
import com.rentle.domain.platform.repository.RoleRepository;
import com.rentle.domain.platform.repository.ScopeRepository;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlatformAdminService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ScopeRepository scopeRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final PermissionResolverService permissionResolverService;

    public PlatformAdminService(PermissionRepository permissionRepository,
                                RoleRepository roleRepository,
                                RolePermissionRepository rolePermissionRepository,
                                ScopeRepository scopeRepository,
                                AssignmentRepository assignmentRepository,
                                UserRepository userRepository,
                                PermissionResolverService permissionResolverService) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.scopeRepository = scopeRepository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.permissionResolverService = permissionResolverService;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> permissions(String domain) {
        List<Permission> permissions = domain == null || domain.isBlank()
                ? permissionRepository.findAllByOrderByKeyAsc()
                : permissionRepository.findByDomainOrderByKeyAsc(domain);
        return permissions.stream().map(PermissionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles() {
        return roleRepository.findAllByOrderByNameAsc().stream().map(this::roleResponse).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse role(UUID roleId) {
        return roleResponse(findRole(roleId));
    }

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        if (roleRepository.findByName(request.name()).isPresent()) {
            throw new RentleException("Role name already exists");
        }
        Set<Permission> permissions = validatePermissions(request.permissionKeys());
        Role role = new Role();
        role.setName(request.name());
        role.setDisplayName(request.displayName());
        role.setDescription(request.description());
        role.setIsSystemRole(false);
        role = roleRepository.save(role);
        replacePermissions(role, permissions);
        return roleResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(UUID roleId, UpdateRoleRequest request) {
        Role role = findRole(roleId);
        Set<Permission> permissions = validatePermissions(request.permissionKeys());
        Set<String> currentKeys = permissionKeys(roleId);
        Set<String> requestedKeys = request.permissionKeys();
        if (SUPER_ADMIN.equals(role.getName()) && !currentKeys.equals(requestedKeys)) {
            throw new RentleException("SUPER_ADMIN permissions cannot be changed");
        }

        role.setDisplayName(request.displayName());
        role.setDescription(request.description());
        roleRepository.save(role);
        if (!currentKeys.equals(requestedKeys)) {
            replacePermissions(role, permissions);
        }
        permissionResolverService.invalidateRole(roleId);
        return roleResponse(role);
    }

    @Transactional
    public void deleteRole(UUID roleId) {
        Role role = findRole(roleId);
        if (Boolean.TRUE.equals(role.getIsSystemRole())) {
            throw new RentleException("System roles cannot be deleted");
        }
        if (assignmentRepository.existsByRoleIdAndRevokedAtIsNull(roleId)) {
            throw new RentleException("Roles with live assignments cannot be deleted");
        }
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> assignments(UUID userId, UUID roleId) {
        return assignmentRepository.findLiveAssignments(userId, roleId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    @Transactional
    public AssignmentResponse createAssignment(CreateAssignmentRequest request) {
        User subject = findUser(request.userId());
        Role role = findRole(request.roleId());
        Scope rootScope = scopeRepository.findFirstByType(ScopeType.ROOT)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ROOT scope not found; IAM enablement has not been completed"));
        if (assignmentRepository.existsBySubjectIdAndRoleIdAndScopeIdAndRevokedAtIsNull(
                subject.getId(), role.getId(), rootScope.getId())) {
            throw new RentleException("This live assignment already exists");
        }

        Assignment assignment = new Assignment();
        assignment.setSubject(subject);
        assignment.setRole(role);
        assignment.setScope(rootScope);
        assignment.setGrantedBy(findUser(SecurityUtils.currentUserId()));
        assignment = assignmentRepository.save(assignment);
        permissionResolverService.invalidate(subject.getId());
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public void revokeAssignment(UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        if (assignment.getRevokedAt() != null) {
            throw new RentleException("Assignment is already revoked");
        }

        UUID actorId = SecurityUtils.currentUserId();
        protectSuperAdminAssignment(assignment, actorId);
        assignment.setRevokedAt(Instant.now());
        assignment.setRevokedBy(findUser(actorId));
        assignmentRepository.save(assignment);
        permissionResolverService.invalidate(assignment.getSubject().getId());
    }

    @Transactional(readOnly = true)
    public UserLookupResponse lookupUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return UserLookupResponse.from(user);
    }

    private void protectSuperAdminAssignment(Assignment assignment, UUID actorId) {
        Role role = assignment.getRole();
        if (!SUPER_ADMIN.equals(role.getName())) {
            return;
        }
        if (assignmentRepository.countByRoleIdAndRevokedAtIsNull(role.getId()) <= 1) {
            throw new RentleException("The last live SUPER_ADMIN assignment cannot be revoked");
        }
        if (actorId.equals(assignment.getSubject().getId())
                && assignmentRepository.countBySubjectIdAndRoleIdAndRevokedAtIsNull(actorId, role.getId()) <= 1) {
            throw new RentleException("You cannot revoke your own last SUPER_ADMIN assignment");
        }
    }

    private Set<Permission> validatePermissions(Set<String> keys) {
        if (keys.isEmpty()) {
            return Set.of();
        }
        List<Permission> permissions = permissionRepository.findByKeyIn(keys);
        Set<String> foundKeys = permissions.stream().map(Permission::getKey).collect(Collectors.toSet());
        Set<String> invalidKeys = keys.stream()
                .filter(key -> !foundKeys.contains(key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        permissions.stream()
                .filter(permission -> Boolean.TRUE.equals(permission.getIsDeprecated()))
                .map(Permission::getKey)
                .forEach(invalidKeys::add);
        if (!invalidKeys.isEmpty()) {
            throw new RentleException("Unknown or deprecated permission keys: " + invalidKeys);
        }
        return new LinkedHashSet<>(permissions);
    }

    private void replacePermissions(Role role, Set<Permission> permissions) {
        rolePermissionRepository.deleteByRoleId(role.getId());
        rolePermissionRepository.flush();
        rolePermissionRepository.saveAll(permissions.stream()
                .map(permission -> new RolePermission(role, permission))
                .toList());
    }

    private RoleResponse roleResponse(Role role) {
        return RoleResponse.from(role, permissionKeys(role.getId()));
    }

    private Set<String> permissionKeys(UUID roleId) {
        return rolePermissionRepository.findByRoleIdWithPermission(roleId).stream()
                .map(RolePermission::getPermission)
                .map(Permission::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Role findRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
