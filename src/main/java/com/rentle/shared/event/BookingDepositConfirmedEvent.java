package com.rentle.shared.event;

import java.util.UUID;

public record BookingDepositConfirmedEvent(
        UUID bookingId,
        String listingTitle,
        String renterPhone
) {}
