package com.rentle.domain.listing.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(
        UUID listingId,
        List<BlockedRange> blocked
) {
    /** rangeId is null for booking-derived blocks (only owner blocks are deletable). */
    public record BlockedRange(
            UUID rangeId,
            LocalDate startDate,
            LocalDate endDate,
            String source          // OWNER_BLOCKED | BOOKED
    ) {}
}
