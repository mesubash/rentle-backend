package com.rentle.unit;

import com.rentle.domain.platform.model.Assignment;
import com.rentle.domain.platform.repository.AssignmentRepository;
import com.rentle.domain.platform.service.PermissionResolverService;
import com.rentle.domain.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionResolverServiceTest {

    private final AssignmentRepository assignmentRepository = mock(AssignmentRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final PermissionResolverService service = new PermissionResolverService(assignmentRepository, redisTemplate);

    PermissionResolverServiceTest() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void cacheMissResolvesFromDatabaseThenCacheHitAvoidsDatabase() {
        UUID userId = UUID.randomUUID();
        String key = "perms:" + userId;
        when(valueOperations.get(key)).thenReturn(null, "kyc.submission.read,platform.role.read");
        when(assignmentRepository.findLivePermissionKeys(userId))
                .thenReturn(Set.of("platform.role.read", "kyc.submission.read"));

        assertEquals(Set.of("platform.role.read", "kyc.submission.read"), service.permissionKeysFor(userId));
        assertEquals(Set.of("platform.role.read", "kyc.submission.read"), service.permissionKeysFor(userId));

        verify(assignmentRepository, times(1)).findLivePermissionKeys(userId);
        verify(valueOperations).set(key, "kyc.submission.read,platform.role.read", Duration.ofMinutes(5));
    }

    @Test
    void emptyPermissionSetIsCached() {
        UUID userId = UUID.randomUUID();
        String key = "perms:" + userId;
        when(valueOperations.get(key)).thenReturn(null, "");
        when(assignmentRepository.findLivePermissionKeys(userId)).thenReturn(Set.of());

        assertEquals(Set.of(), service.permissionKeysFor(userId));
        assertEquals(Set.of(), service.permissionKeysFor(userId));

        verify(assignmentRepository, times(1)).findLivePermissionKeys(userId);
        verify(valueOperations).set(key, "", Duration.ofMinutes(5));
    }

    @Test
    void invalidateDeletesTheUsersCacheKey() {
        UUID userId = UUID.randomUUID();

        service.invalidate(userId);

        verify(redisTemplate).delete("perms:" + userId);
        verify(assignmentRepository, never()).findLivePermissionKeys(userId);
    }

    @Test
    void invalidateRoleDeletesEachDistinctLiveAssigneesCacheKey() {
        UUID roleId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        Assignment first = assignment(firstUserId);
        Assignment duplicate = assignment(firstUserId);
        Assignment second = assignment(secondUserId);
        when(assignmentRepository.findByRoleIdAndRevokedAtIsNull(roleId))
                .thenReturn(List.of(first, duplicate, second));

        service.invalidateRole(roleId);

        verify(redisTemplate, times(1)).delete("perms:" + firstUserId);
        verify(redisTemplate, times(1)).delete("perms:" + secondUserId);
    }

    private Assignment assignment(UUID userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Assignment assignment = mock(Assignment.class);
        when(assignment.getSubject()).thenReturn(user);
        return assignment;
    }
}
