package com.rentle.shared.event;

import java.util.UUID;

public record BookingRejectedEvent(
        UUID bookingId,
        String listingTitle,
        String renterPhone
) {}
