package com.rentle.domain.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/** Per-booking messaging summary for the inbox: latest activity + unread count for the viewer. */
public record ThreadSummary(
        UUID bookingId,
        Instant lastMessageAt,
        Long unreadCount
) {}
