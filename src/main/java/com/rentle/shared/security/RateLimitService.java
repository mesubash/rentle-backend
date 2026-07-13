package com.rentle.shared.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RateLimitService {

    // Atomic fixed-window: INCR then set TTL on first hit, in one round trip so a
    // crash between the two can never leave a key without an expiry.
    private static final RedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Fixed-window counter. Returns true while under the limit. */
    public boolean allow(String key, int limit, Duration window) {
        Long count = redis.execute(
                INCR_WITH_TTL,
                List.of("rl:" + key),
                String.valueOf(window.toMillis()));
        return count == null || count <= limit;
    }

    /** Clears a counter — e.g. a successful login must not consume brute-force quota. */
    public void reset(String key) {
        redis.delete("rl:" + key);
    }
}
