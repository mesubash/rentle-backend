package com.rentle.integration;

import com.rentle.domain.platform.model.Assignment;
import com.rentle.domain.platform.model.Role;
import com.rentle.domain.platform.model.Scope;
import com.rentle.domain.platform.model.ScopeType;
import com.rentle.domain.platform.repository.AssignmentRepository;
import com.rentle.domain.platform.repository.RoleRepository;
import com.rentle.domain.platform.repository.ScopeRepository;
import com.rentle.domain.platform.service.IamCatalogSynchronizer;
import com.rentle.domain.platform.service.PermissionResolverService;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.security.JwtTokenService;

import java.util.UUID;

final class IamTestSupport {

    private final IamCatalogSynchronizer synchronizer;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ScopeRepository scopeRepository;
    private final AssignmentRepository assignmentRepository;
    private final PermissionResolverService permissionResolverService;
    private final JwtTokenService jwtTokenService;

    IamTestSupport(IamCatalogSynchronizer synchronizer,
                   UserRepository userRepository,
                   RoleRepository roleRepository,
                   ScopeRepository scopeRepository,
                   AssignmentRepository assignmentRepository,
                   PermissionResolverService permissionResolverService,
                   JwtTokenService jwtTokenService) {
        this.synchronizer = synchronizer;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.scopeRepository = scopeRepository;
        this.assignmentRepository = assignmentRepository;
        this.permissionResolverService = permissionResolverService;
        this.jwtTokenService = jwtTokenService;
    }

    User createUser(String prefix) {
        User user = new User();
        user.setEmail(prefix + "-" + suffix() + "@test.com");
        user.setFullName(prefix);
        user.setStatus(UserStatus.VERIFIED);
        user.setEmailVerified(true);
        user.setPhoneVerified(true);
        user.setCitizenshipVerified(true);
        return userRepository.save(user);
    }

    User createStaff(String prefix, String roleName) {
        User user = createUser(prefix);
        assignRole(user, roleName);
        return user;
    }

    Assignment assignRole(User user, String roleName) {
        synchronizer.synchronize();
        Role role = roleRepository.findByName(roleName).orElseThrow();
        Scope root = scopeRepository.findFirstByType(ScopeType.ROOT).orElseThrow();
        Assignment existing = assignmentRepository.findBySubjectIdAndRevokedAtIsNull(user.getId()).stream()
                .filter(assignment -> assignment.getRole().getId().equals(role.getId())
                        && assignment.getScope().getId().equals(root.getId()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            permissionResolverService.invalidate(user.getId());
            return existing;
        }

        Assignment assignment = new Assignment();
        assignment.setSubject(user);
        assignment.setRole(role);
        assignment.setScope(root);
        assignment.setGrantedBy(null);
        assignment = assignmentRepository.save(assignment);
        permissionResolverService.invalidate(user.getId());
        return assignment;
    }

    String token(User user) {
        return jwtTokenService.createAccessToken(user.getId(), user.getRole().name(), user.getStatus().name());
    }

    String authorization(User user) {
        return "Bearer " + token(user);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
