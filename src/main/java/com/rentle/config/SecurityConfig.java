package com.rentle.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.rentle.domain.platform.service.PermissionResolverService;
import com.rentle.shared.api.JsonErrorWriter;
import com.rentle.shared.security.JwtKeyProvider;
import com.rentle.shared.security.TokenRevocationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final PermissionResolverService permissionResolverService;
    private final RentleProperties rentleProperties;

    public SecurityConfig(PermissionResolverService permissionResolverService,
                          RentleProperties rentleProperties) {
        this.permissionResolverService = permissionResolverService;
        this.rentleProperties = rentleProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationConverter jwtAuthConverter,
                                           AuthenticationEntryPoint authenticationEntryPoint,
                                           AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            // No CORS config: the app is reached through a same-origin BFF proxy, so
            // the browser never calls this API cross-origin. A direct API consumer
            // uses the bearer token in the Authorization header (not subject to CORS).
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/listings/**",
                        "/api/v1/categories/**",
                        "/api/v1/users/*",
                        "/api/v1/users/*/listings",
                        "/api/v1/users/*/reviews").permitAll()
                .requestMatchers("/files/**", "/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v1/admin/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (role != null) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
            if (rentleProperties.iam().enabled()) {
                UUID userId = UUID.fromString(jwt.getSubject());
                permissionResolverService.permissionKeysFor(userId).stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtKeyProvider keys) {
        RSAKey rsaKey = new RSAKey.Builder(keys.publicKey())
                .privateKey(keys.privateKey())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtKeyProvider keys, TokenRevocationService revocation) {
        NimbusJwtDecoder delegate = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
        // Reject tokens revoked at logout (by jti) or when the user is suspended
        // (by subject) before their natural 15-minute expiry.
        return token -> {
            Jwt jwt = delegate.decode(token);
            if (revocation.isTokenRevoked(jwt.getId())) {
                throw new JwtException("Token has been revoked");
            }
            if (revocation.isUserRevoked(jwt.getSubject())) {
                throw new JwtException("Account access has been revoked");
            }
            return jwt;
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** 401 in the standard envelope for missing/invalid/expired tokens. */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) ->
                JsonErrorWriter.write(response, HttpStatus.UNAUTHORIZED.value(), "Authentication required");
    }

    /** 403 in the standard envelope for authenticated-but-forbidden requests. */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                JsonErrorWriter.write(response, HttpStatus.FORBIDDEN.value(), "You do not have access to this resource");
    }
}
