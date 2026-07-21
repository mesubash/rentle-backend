package com.rentle.domain.messaging.dto;

import com.rentle.domain.booking.model.BookingStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-booking messaging summary for the inbox: latest activity, unread count, and enough
 * booking context to render a row.
 *
 * The context fields exist so the inbox does not have to fetch every booking the user
 * participates in (as renter and as owner) purely to look up a title and a name.
 */
public record ThreadSummary(
        UUID bookingId,
        Instant lastMessageAt,
        Long unreadCount,
        String listingTitle,
        UUID ownerId,
        String ownerName,
        String renterName,
        BookingStatus status
) {}
