package com.rentle.domain.notification.service;

import com.rentle.domain.notification.dto.NotificationResponse;
import com.rentle.domain.notification.model.Notification;
import com.rentle.domain.notification.repository.NotificationRepository;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Persist a notification for a user. Best-effort: callers should not fail their
     *  main operation if this throws — wrap at the call site if needed. */
    public void notify(UUID userId, String type, String message, String link) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setMessage(message);
        n.setLink(link);
        repository.save(n);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, Pageable pageable) {
        return PageResponse.from(
                repository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    public void markRead(UUID userId, UUID notificationId) {
        Notification n = repository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!n.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Notification not found");
        }
        n.setRead(true);
        repository.save(n);
    }

    public void markAllRead(UUID userId) {
        repository.markAllRead(userId);
    }
}
