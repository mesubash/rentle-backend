package com.rentle.shared.event;

import java.util.UUID;

public record BookingCompletedEvent(
        UUID bookingId,
        String listingTitle,
        String ownerPhone,
        String renterPhone
) {}
