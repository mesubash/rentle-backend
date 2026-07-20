package com.rentle.domain.notification.controller;

import com.rentle.domain.notification.dto.NotificationResponse;
import com.rentle.domain.notification.service.NotificationService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(notificationService.list(SecurityUtils.currentUserId(), pageable));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount() {
        return ApiResponse.ok(notificationService.unreadCount(SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<String> markRead(@PathVariable UUID id) {
        notificationService.markRead(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("Marked read");
    }

    @PutMapping("/read-all")
    public ApiResponse<String> markAllRead() {
        notificationService.markAllRead(SecurityUtils.currentUserId());
        return ApiResponse.ok("All marked read");
    }
}
