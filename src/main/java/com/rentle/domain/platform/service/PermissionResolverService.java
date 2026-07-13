package com.rentle.domain.platform.service;

import com.rentle.domain.platform.model.Assignment;
import com.rentle.domain.platform.repository.AssignmentRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PermissionResolverService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String CACHE_PREFIX = "perms:";

    private final AssignmentRepository assignmentRepository;
    private final StringRedisTemplate redisTemplate;

    public PermissionResolverService(AssignmentRepository assignmentRepository,
                                     StringRedisTemplate redisTemplate) {
        this.assignmentRepository = assignmentRepository;
        this.redisTemplate = redisTemplate;
    }

    public Set<String> permissionKeysFor(UUID userId) {
        String cacheKey = cacheKey(userId);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (cached.isEmpty()) {
                return Set.of();
            }
            return Set.copyOf(Arrays.asList(cached.split(",")));
        }

        Set<String> resolved = assignmentRepository.findLivePermissionKeys(userId);
        String serialized = resolved.stream().sorted().reduce((left, right) -> left + "," + right).orElse("");
        redisTemplate.opsForValue().set(cacheKey, serialized, CACHE_TTL);
        return Set.copyOf(resolved);
    }

    public void invalidate(UUID userId) {
        redisTemplate.delete(cacheKey(userId));
    }

    @Transactional(readOnly = true)
    public void invalidateRole(UUID roleId) {
        List<Assignment> assignments = assignmentRepository.findByRoleIdAndRevokedAtIsNull(roleId);
        Set<UUID> userIds = new LinkedHashSet<>();
        assignments.forEach(assignment -> userIds.add(assignment.getSubject().getId()));
        userIds.forEach(this::invalidate);
    }

    private String cacheKey(UUID userId) {
        return CACHE_PREFIX + userId;
    }
}
