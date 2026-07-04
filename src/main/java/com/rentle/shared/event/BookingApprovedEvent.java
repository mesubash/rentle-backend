package com.rentle.shared.event;

import java.util.UUID;

public record BookingApprovedEvent(
        UUID bookingId,
        String listingTitle,
        String renterPhone
) {}
