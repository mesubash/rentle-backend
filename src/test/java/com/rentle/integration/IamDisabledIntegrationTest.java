package com.rentle.integration;

import com.rentle.config.RentleProperties;
import com.rentle.config.TestcontainersConfig;
import com.rentle.domain.platform.repository.AssignmentRepository;
import com.rentle.domain.platform.repository.PermissionRepository;
import com.rentle.domain.platform.repository.RoleRepository;
import com.rentle.domain.platform.repository.ScopeRepository;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "rentle.iam.enabled=false",
        "rentle.iam.sync-catalog=false"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class IamDisabledIntegrationTest {

    @Autowired RentleProperties rentleProperties;
    @Autowired JwtAuthenticationConverter jwtAuthenticationConverter;
    @Autowired PermissionRepository permissionRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired ScopeRepository scopeRepository;
    @Autowired AssignmentRepository assignmentRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService jwtTokenService;
    @Autowired MockMvc mockMvc;

    @Test
    void disabledIamKillSwitchAddsNoPermissionAuthorities() throws Exception {
        assertFalse(rentleProperties.iam().enabled());
        assertFalse(rentleProperties.iam().syncCatalog());
        assertEquals(0, permissionRepository.count());
        assertEquals(0, roleRepository.count());
        assertEquals(0, scopeRepository.count());
        assertEquals(0, assignmentRepository.count());
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("role", "ADMIN")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        Set<String> authorities = jwtAuthenticationConverter.convert(jwt).getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());

        assertEquals(Set.of("FACTOR_BEARER"), authorities);

        User user = new User();
        user.setEmail("kill-switch-" + UUID.randomUUID() + "@test.com");
        user.setFullName("Kill Switch User");
        user.setStatus(UserStatus.VERIFIED);
        user = userRepository.save(user);
        String authorization = "Bearer " + jwtTokenService.createAccessToken(
                user.getId(), user.getStatus().name());

        for (String path : Set.of("/api/v1/admin/users", "/api/v1/platform/roles")) {
            mockMvc.perform(get(path).header("Authorization", authorization))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("You do not have access to this resource"));
        }
    }
}
