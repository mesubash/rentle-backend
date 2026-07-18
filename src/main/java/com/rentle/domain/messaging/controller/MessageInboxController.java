package com.rentle.domain.messaging.controller;

import com.rentle.domain.messaging.dto.ThreadSummary;
import com.rentle.domain.messaging.service.MessageService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Cross-booking messaging views (the per-thread endpoints live under
 * /bookings/{id}/messages). Backs the navigation unread badge.
 */
@RestController
@RequestMapping("/api/v1/messages")
public class MessageInboxController {

    private final MessageService messageService;

    public MessageInboxController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        long count = messageService.unreadCount(SecurityUtils.currentUserId());
        return ApiResponse.ok(Map.of("count", count));
    }

    /** Per-thread summaries (last activity + unread count) for the inbox list. */
    @GetMapping("/threads")
    public ApiResponse<List<ThreadSummary>> threads() {
        return ApiResponse.ok(messageService.threadSummaries(SecurityUtils.currentUserId()));
    }
}
