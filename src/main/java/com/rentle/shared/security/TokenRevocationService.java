package com.rentle.shared.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Central place for JWT revocation state in Redis. Access tokens carry a 15-minute
 * TTL, so revocation markers only need to outlive that window.
 */
@Service
public class TokenRevocationService {

    private static final String JTI_PREFIX = "bl:";
    private static final String USER_PREFIX = "susp:";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public TokenRevocationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Blacklist a single access token by its jti until it would have expired. */
    public void revokeToken(String jti, Duration remaining) {
        if (jti != null && remaining != null && !remaining.isNegative() && !remaining.isZero()) {
            redis.opsForValue().set(JTI_PREFIX + jti, "1", remaining);
        }
    }

    /** Reject every currently-live access token for a user (e.g. on suspension). */
    public void revokeUser(UUID userId) {
        redis.opsForValue().set(USER_PREFIX + userId, "1", ACCESS_TOKEN_TTL);
    }

    public void clearUser(UUID userId) {
        redis.delete(USER_PREFIX + userId);
    }

    public boolean isTokenRevoked(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(JTI_PREFIX + jti));
    }

    public boolean isUserRevoked(String subject) {
        return subject != null && Boolean.TRUE.equals(redis.hasKey(USER_PREFIX + subject));
    }
}
