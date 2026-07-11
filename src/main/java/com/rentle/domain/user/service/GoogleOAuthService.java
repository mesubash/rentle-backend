package com.rentle.domain.user.service;

import com.rentle.config.GoogleProperties;
import com.rentle.config.RentleProperties;
import com.rentle.domain.user.dto.AuthResponse;
import com.rentle.domain.user.model.User;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.UnauthorizedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Backend-driven Google OAuth (Authorization Code flow). The frontend holds no
 * Google configuration: it just links to /auth/google/login. Google returns to
 * the backend callback, which exchanges the code with the client secret and hands
 * a short-lived one-time code back to the frontend to swap for a session — so the
 * httpOnly session cookies are set on the app origin, not the API origin, and no
 * token ever rides in a URL.
 */
@Service
public class GoogleOAuthService {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String STATE_PREFIX = "oauthstate:";
    private static final String HANDOFF_PREFIX = "oauthhandoff:";
    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final Duration HANDOFF_TTL = Duration.ofMinutes(2);

    private final GoogleProperties google;
    private final RentleProperties props;
    private final StringRedisTemplate redis;
    private final AuthService authService;
    private final RestClient restClient = RestClient.create();
    private final SecureRandom random = new SecureRandom();

    public GoogleOAuthService(GoogleProperties google,
                              RentleProperties props,
                              StringRedisTemplate redis,
                              AuthService authService) {
        this.google = google;
        this.props = props;
        this.redis = redis;
        this.authService = authService;
    }

    public boolean isEnabled() {
        return google.isOAuthConfigured();
    }

    /** Build the Google consent URL and remember the state token (CSRF). */
    public String startUrl() {
        requireEnabled();
        String state = randomToken();
        redis.opsForValue().set(STATE_PREFIX + state, "1", STATE_TTL);
        return UriComponentsBuilder.fromUriString(AUTH_URL)
                .queryParam("client_id", google.clientId())
                .queryParam("redirect_uri", google.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .build()
                .toUriString();
    }

    /**
     * Handle Google's callback: validate state, exchange the code for an ID token,
     * resolve the account, and return the frontend URL carrying a one-time handoff.
     */
    public String handleCallback(String code, String state) {
        requireEnabled();
        if (state == null || Boolean.FALSE.equals(redis.delete(STATE_PREFIX + state))) {
            throw new UnauthorizedException("Invalid or expired OAuth state");
        }
        String idToken = exchangeCodeForIdToken(code);
        User user = authService.resolveGoogleUser(idToken);

        String handoff = randomToken();
        redis.opsForValue().set(HANDOFF_PREFIX + handoff, user.getId().toString(), HANDOFF_TTL);
        return props.appUrl() + "/auth/google/callback?code=" + handoff;
    }

    /** Swap a one-time handoff code for a real session. */
    public AuthResponse exchangeHandoff(String handoff) {
        String userId = redis.opsForValue().getAndDelete(HANDOFF_PREFIX + handoff);
        if (userId == null) {
            throw new UnauthorizedException("Invalid or expired sign-in code");
        }
        return authService.issueSessionFor(java.util.UUID.fromString(userId));
    }

    private String exchangeCodeForIdToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", google.clientId());
        form.add("client_secret", google.clientSecret());
        form.add("redirect_uri", google.redirectUri());
        form.add("grant_type", "authorization_code");
        try {
            Map<?, ?> body = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object idToken = body != null ? body.get("id_token") : null;
            if (idToken == null) {
                throw new UnauthorizedException("Google did not return an ID token");
            }
            return idToken.toString();
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("Google token exchange failed");
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new RentleException("Google sign-in is not configured on this server");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
