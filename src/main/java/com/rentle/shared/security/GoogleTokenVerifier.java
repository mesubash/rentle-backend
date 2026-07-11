package com.rentle.shared.security;

import com.rentle.config.GoogleProperties;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.UnauthorizedException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Verifies a Google Sign-In ID token: signature against Google's published JWKS,
 * plus issuer and audience (our OAuth client id). The frontend obtains the token
 * from Google Identity Services and posts it; we never handle Google passwords.
 */
@Component
public class GoogleTokenVerifier {

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> VALID_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final GoogleProperties props;
    private volatile NimbusJwtDecoder decoder;

    public GoogleTokenVerifier(GoogleProperties props) {
        this.props = props;
    }

    public record GoogleIdentity(String subject, String email, boolean emailVerified, String name, String picture) {}

    public GoogleIdentity verify(String idToken) {
        if (!props.isConfigured()) {
            throw new RentleException("Google sign-in is not configured on this server");
        }
        try {
            Jwt jwt = decoder().decode(idToken);
            String email = jwt.getClaimAsString("email");
            if (email == null) {
                throw new UnauthorizedException("Google account has no email");
            }
            return new GoogleIdentity(
                    jwt.getSubject(),
                    email,
                    Boolean.TRUE.equals(jwt.getClaim("email_verified")),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("picture"));
        } catch (JwtException e) {
            throw new UnauthorizedException("Invalid Google sign-in token");
        }
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder local = decoder;
        if (local == null) {
            synchronized (this) {
                local = decoder;
                if (local == null) {
                    local = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
                    local.setJwtValidator(new DelegatingValidator(props.clientId()));
                    decoder = local;
                }
            }
        }
        return local;
    }

    /** Validate standard claims (exp/nbf), issuer is Google, audience is our client id. */
    private record DelegatingValidator(String clientId) implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            OAuth2TokenValidatorResult base = JwtValidators.createDefault().validate(jwt);
            if (base.hasErrors()) return base;
            if (jwt.getIssuer() == null || !VALID_ISSUERS.contains(jwt.getIssuer().toString())) {
                return OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_issuer"));
            }
            if (jwt.getAudience() == null || !jwt.getAudience().contains(clientId)) {
                return OAuth2TokenValidatorResult.failure(
                        new org.springframework.security.oauth2.core.OAuth2Error("invalid_audience"));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
