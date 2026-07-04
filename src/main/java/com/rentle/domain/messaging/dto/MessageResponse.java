package com.rentle.domain.messaging.dto;

import com.rentle.domain.messaging.model.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID bookingId,
        UUID senderId,
        String senderName,
        String content,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getBooking().getId(),
                m.getSender().getId(),
                m.getSender().getFullName(),
                m.getContent(),
                Boolean.TRUE.equals(m.getIsRead()),
                m.getReadAt(),
                m.getCreatedAt()
        );
    }
}
