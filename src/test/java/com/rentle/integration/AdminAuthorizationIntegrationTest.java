package com.rentle.integration;

import com.rentle.config.TestcontainersConfig;
import com.rentle.domain.platform.repository.AssignmentRepository;
import com.rentle.domain.platform.repository.RoleRepository;
import com.rentle.domain.platform.repository.ScopeRepository;
import com.rentle.domain.platform.service.IamCatalogSynchronizer;
import com.rentle.domain.platform.service.PermissionResolverService;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class AdminAuthorizationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired IamCatalogSynchronizer synchronizer;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired ScopeRepository scopeRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired PermissionResolverService permissionResolverService;
    @Autowired JwtTokenService jwtTokenService;

    private IamTestSupport iam;

    @BeforeEach
    void setUp() {
        iam = new IamTestSupport(
                synchronizer,
                userRepository,
                roleRepository,
                scopeRepository,
                assignmentRepository,
                permissionResolverService,
                jwtTokenService
        );
    }

    @Test
    void superAdminCanUseAdminAndPlatformSurfaces() throws Exception {
        User superAdmin = iam.createStaff("super-admin", "SUPER_ADMIN");
        String authorization = iam.authorization(superAdmin);

        for (RequestBuilder request : List.of(
                get("/api/v1/admin/users").header("Authorization", authorization),
                get("/api/v1/admin/kyc").header("Authorization", authorization),
                get("/api/v1/admin/listings").header("Authorization", authorization),
                get("/api/v1/admin/bookings").header("Authorization", authorization),
                get("/api/v1/platform/roles").header("Authorization", authorization))) {
            mockMvc.perform(request).andExpect(status().isOk());
        }
    }

    @Test
    void adminCanOperateMarketplaceAdministrationButNotPlatformRoles() throws Exception {
        User admin = iam.createStaff("admin", "ADMIN");
        User target = iam.createUser("target");
        String authorization = iam.authorization(admin);

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", target.getId())
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        expectForbidden(get("/api/v1/platform/roles").header("Authorization", authorization));
    }

    @Test
    void kycReviewerReadsKycButCannotSuspendOrManageRoles() throws Exception {
        User reviewer = iam.createStaff("kyc-reviewer", "KYC_REVIEWER");
        String authorization = iam.authorization(reviewer);

        mockMvc.perform(get("/api/v1/admin/kyc").header("Authorization", authorization))
                .andExpect(status().isOk());
        expectForbidden(put("/api/v1/admin/users/{id}/suspend", UUID.randomUUID())
                .header("Authorization", authorization));
        expectForbidden(get("/api/v1/platform/roles").header("Authorization", authorization));
    }

    @Test
    void supportReadsOperationalListsButCannotApproveOrSuspend() throws Exception {
        User support = iam.createStaff("support", "SUPPORT");
        String authorization = iam.authorization(support);

        for (RequestBuilder request : List.of(
                get("/api/v1/admin/users").header("Authorization", authorization),
                get("/api/v1/admin/listings").header("Authorization", authorization),
                get("/api/v1/admin/bookings").header("Authorization", authorization))) {
            mockMvc.perform(request).andExpect(status().isOk());
        }
        expectForbidden(put("/api/v1/admin/users/{id}/verify", UUID.randomUUID())
                .header("Authorization", authorization));
        expectForbidden(put("/api/v1/admin/users/{id}/suspend", UUID.randomUUID())
                .header("Authorization", authorization));
    }

    @Test
    void plainUserIsForbiddenFromEveryAdminList() throws Exception {
        User user = iam.createUser("plain-user");
        String authorization = iam.authorization(user);

        for (RequestBuilder request : List.of(
                get("/api/v1/admin/users").header("Authorization", authorization),
                get("/api/v1/admin/kyc").header("Authorization", authorization),
                get("/api/v1/admin/listings").header("Authorization", authorization),
                get("/api/v1/admin/bookings").header("Authorization", authorization))) {
            expectForbidden(request);
        }
    }

    private void expectForbidden(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error").value("You do not have access to this resource"));
    }
}
