package com.rentle.unit;

import com.rentle.config.RentleProperties;
import com.rentle.config.SecurityConfig;
import com.rentle.domain.platform.service.PermissionResolverService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityConfigIamFlagTest {

    @Test
    void disabledIamFlagIgnoresLegacyRoleClaimAndAddsNoPermissionAuthorities() {
        PermissionResolverService resolver = mock(PermissionResolverService.class);
        RentleProperties properties = mock(RentleProperties.class);
        when(properties.iam()).thenReturn(new RentleProperties.Iam(false, false, null));
        SecurityConfig securityConfig = new SecurityConfig(resolver, properties);
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .claim("role", "ADMIN")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) securityConfig
                .jwtAuthenticationConverter()
                .convert(jwt);

        assertEquals(Set.of("FACTOR_BEARER"), authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet()));
        verify(resolver, never()).permissionKeysFor(userId);
    }
}
