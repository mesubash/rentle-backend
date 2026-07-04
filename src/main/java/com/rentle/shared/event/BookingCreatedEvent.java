package com.rentle.shared.event;

import java.util.UUID;

public record BookingCreatedEvent(
        UUID bookingId,
        String listingTitle,
        String ownerPhone
) {}
