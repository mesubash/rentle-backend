package com.rentle.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentle.config.TestcontainersConfig;
import com.rentle.domain.platform.catalog.PermissionKeys;
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
import com.rentle.domain.platform.service.PermissionResolverService;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rentle.iam.enabled=true",
        "rentle.iam.sync-catalog=false"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class PlatformIamIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired RolePermissionRepository rolePermissionRepository;
    @Autowired ScopeRepository scopeRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired PermissionResolverService permissionResolverService;
    @Autowired JwtTokenService jwtTokenService;

    @Test
    void roleCrudRoundTrip() throws Exception {
        User admin = user("role-admin");
        Role accessRole = role("ROLE_ACCESS_" + suffix(), false, Set.of(
                PermissionKeys.PLATFORM_ROLE_READ,
                PermissionKeys.PLATFORM_ROLE_MANAGE
        ));
        assign(admin, accessRole, rootScope(), admin);
        String token = token(admin);

        JsonNode created = responseJson(mockMvc.perform(post("/api/v1/platform/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RolePayload(
                                "CUSTOM_" + suffix(), "Custom", "First", Set.of(PermissionKeys.IDENTITY_USER_READ)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.permissionKeys[0]").value(PermissionKeys.IDENTITY_USER_READ))
                .andReturn().getResponse().getContentAsString());
        UUID roleId = UUID.fromString(created.path("data").path("id").asText());

        mockMvc.perform(get("/api/v1/platform/roles/{id}", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Custom"));

        mockMvc.perform(put("/api/v1/platform/roles/{id}", roleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRolePayload(
                                "Updated", "Second", Set.of(PermissionKeys.PLATFORM_ROLE_READ)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated"))
                .andExpect(jsonPath("$.data.permissionKeys[0]").value(PermissionKeys.PLATFORM_ROLE_READ));

        mockMvc.perform(delete("/api/v1/platform/roles/{id}", roleId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertTrue(roleRepository.findById(roleId).isEmpty());
    }

    @Test
    void assignmentGrantAndRevokeInvalidatePermissionCache() throws Exception {
        User admin = user("assignment-admin");
        User subject = user("assignment-subject");
        Scope root = rootScope();
        Role accessRole = role("ASSIGNMENT_ACCESS_" + suffix(), false, Set.of(
                PermissionKeys.PLATFORM_ASSIGNMENT_READ,
                PermissionKeys.PLATFORM_ASSIGNMENT_MANAGE
        ));
        Role grantedRole = role("GRANTED_" + suffix(), false, Set.of(PermissionKeys.IDENTITY_USER_READ));
        assign(admin, accessRole, root, admin);
        String token = token(admin);

        assertEquals(Set.of(), permissionResolverService.permissionKeysFor(subject.getId()));
        JsonNode created = responseJson(mockMvc.perform(post("/api/v1/platform/assignments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignmentPayload(subject.getId(), grantedRole.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        UUID assignmentId = UUID.fromString(created.path("data").path("id").asText());
        assertEquals(Set.of(PermissionKeys.IDENTITY_USER_READ),
                permissionResolverService.permissionKeysFor(subject.getId()));

        mockMvc.perform(delete("/api/v1/platform/assignments/{id}", assignmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertEquals(Set.of(), permissionResolverService.permissionKeysFor(subject.getId()));
    }

    @Test
    void systemRoleDeletionIsRefused() throws Exception {
        User admin = user("system-role-admin");
        Role accessRole = role("SYSTEM_DELETE_ACCESS_" + suffix(), false,
                Set.of(PermissionKeys.PLATFORM_ROLE_MANAGE));
        Role systemRole = role("SYSTEM_" + suffix(), true, Set.of());
        assign(admin, accessRole, rootScope(), admin);

        mockMvc.perform(delete("/api/v1/platform/roles/{id}", systemRole.getId())
                        .header("Authorization", bearer(token(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("System roles cannot be deleted"));
    }

    @Test
    void lastSuperAdminAssignmentCannotBeRevoked() throws Exception {
        User admin = user("super-admin");
        Role superAdmin = role("SUPER_ADMIN", true, Set.of(PermissionKeys.PLATFORM_ASSIGNMENT_MANAGE));
        Assignment assignment = assign(admin, superAdmin, rootScope(), admin);

        mockMvc.perform(delete("/api/v1/platform/assignments/{id}", assignment.getId())
                        .header("Authorization", bearer(token(admin))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The last live SUPER_ADMIN assignment cannot be revoked"));
    }

    @Test
    void userWithNoAssignmentsGetsEmptyPermissions() throws Exception {
        User user = user("plain-user");

        mockMvc.perform(get("/api/v1/users/me/permissions")
                        .header("Authorization", bearer(token(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void allPlatformEndpointsForbidUsersWithoutMatchingPermissions() throws Exception {
        User user = user("forbidden-user");
        String authorization = bearer(token(user));
        UUID id = UUID.randomUUID();
        List<RequestBuilder> requests = new ArrayList<>();
        requests.add(get("/api/v1/platform/permissions").header("Authorization", authorization));
        requests.add(get("/api/v1/platform/roles").header("Authorization", authorization));
        requests.add(get("/api/v1/platform/roles/{id}", id).header("Authorization", authorization));
        requests.add(post("/api/v1/platform/roles").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RolePayload("NO_ACCESS", "No access", null, Set.of()))));
        requests.add(put("/api/v1/platform/roles/{id}", id).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateRolePayload("No access", null, Set.of()))));
        requests.add(delete("/api/v1/platform/roles/{id}", id).header("Authorization", authorization));
        requests.add(get("/api/v1/platform/assignments").header("Authorization", authorization));
        requests.add(post("/api/v1/platform/assignments").header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AssignmentPayload(id, id))));
        requests.add(delete("/api/v1/platform/assignments/{id}", id).header("Authorization", authorization));
        requests.add(get("/api/v1/platform/users/lookup").param("email", user.getEmail())
                .header("Authorization", authorization));

        for (RequestBuilder request : requests) {
            mockMvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.error").value("You do not have access to this resource"));
        }
    }

    private User user(String prefix) {
        User user = new User();
        user.setEmail(prefix + "-" + suffix() + "@test.com");
        user.setFullName(prefix);
        user.setStatus(UserStatus.VERIFIED);
        user.setEmailVerified(true);
        user.setPhoneVerified(true);
        user.setCitizenshipVerified(true);
        return userRepository.save(user);
    }

    private Scope rootScope() {
        Scope scope = new Scope();
        scope.setType(ScopeType.ROOT);
        scope.setName("ROOT " + suffix());
        return scopeRepository.save(scope);
    }

    private Role role(String name, boolean systemRole, Set<String> permissionKeys) {
        Role role = new Role();
        role.setName(name);
        role.setDisplayName(name);
        role.setIsSystemRole(systemRole);
        role = roleRepository.save(role);
        for (String key : permissionKeys) {
            Permission permission = permissionRepository.findByKey(key).orElseGet(() -> permission(key));
            rolePermissionRepository.save(new RolePermission(role, permission));
        }
        return role;
    }

    private Permission permission(String key) {
        String[] segments = key.split("\\.");
        Permission permission = new Permission();
        permission.setKey(key);
        permission.setDomain(segments[0]);
        permission.setResource(segments[1]);
        permission.setAction(segments[2]);
        permission.setDescription(key);
        return permissionRepository.save(permission);
    }

    private Assignment assign(User subject, Role role, Scope scope, User grantedBy) {
        Assignment assignment = new Assignment();
        assignment.setSubject(subject);
        assignment.setRole(role);
        assignment.setScope(scope);
        assignment.setGrantedBy(grantedBy);
        assignment = assignmentRepository.save(assignment);
        permissionResolverService.invalidate(subject.getId());
        return assignment;
    }

    private String token(User user) {
        return jwtTokenService.createAccessToken(user.getId(), user.getRole().name(), user.getStatus().name());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private JsonNode responseJson(String response) throws Exception {
        return objectMapper.readTree(response);
    }

    private record RolePayload(String name, String displayName, String description, Set<String> permissionKeys) {}
    private record UpdateRolePayload(String displayName, String description, Set<String> permissionKeys) {}
    private record AssignmentPayload(UUID userId, UUID roleId) {}
}
