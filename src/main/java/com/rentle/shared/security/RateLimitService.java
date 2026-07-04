package com.rentle.shared.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Fixed-window counter. Returns true while under the limit. */
    public boolean allow(String key, int limit, Duration window) {
        String redisKey = "rl:" + key;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redis.expire(redisKey, window);
        }
        return count == null || count <= limit;
    }
}
