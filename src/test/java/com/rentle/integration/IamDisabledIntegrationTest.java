package com.rentle.integration;

import com.rentle.config.RentleProperties;
import com.rentle.config.TestcontainersConfig;
import com.rentle.domain.platform.repository.AssignmentRepository;
import com.rentle.domain.platform.repository.PermissionRepository;
import com.rentle.domain.platform.repository.RoleRepository;
import com.rentle.domain.platform.repository.ScopeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Import(TestcontainersConfig.class)
class IamDisabledIntegrationTest {

    @Autowired RentleProperties rentleProperties;
    @Autowired JwtAuthenticationConverter jwtAuthenticationConverter;
    @Autowired PermissionRepository permissionRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired ScopeRepository scopeRepository;
    @Autowired AssignmentRepository assignmentRepository;

    @Test
    void defaultFlagsKeepLegacyAuthoritiesUnchanged() {
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

        assertEquals(Set.of("ROLE_ADMIN", "FACTOR_BEARER"), authorities);
    }
}
