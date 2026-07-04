package com.rentle.domain.messaging.controller;

import com.rentle.domain.messaging.dto.MessageResponse;
import com.rentle.domain.messaging.dto.SendMessageRequest;
import com.rentle.domain.messaging.service.MessageService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<PageResponse<MessageResponse>> list(
            @PathVariable UUID bookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(messageService.getMessages(SecurityUtils.currentUserId(), bookingId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MessageResponse> send(@PathVariable UUID bookingId,
                                             @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.ok(messageService.send(SecurityUtils.currentUserId(), bookingId, request));
    }

    @PutMapping("/read")
    public ApiResponse<Integer> markRead(@PathVariable UUID bookingId) {
        return ApiResponse.ok(messageService.markAllRead(SecurityUtils.currentUserId(), bookingId));
    }
}
