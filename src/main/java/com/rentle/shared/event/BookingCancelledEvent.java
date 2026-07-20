package com.rentle.shared.event;

import java.util.UUID;

/**
 * A booking was cancelled. The counterparty (whoever did not cancel) is notified,
 * so a cancellation never silently strands the other side.
 */
public record BookingCancelledEvent(
        UUID bookingId,
        String listingTitle,
        String ownerPhone,
        String renterPhone,
        boolean cancelledByOwner
) {}
