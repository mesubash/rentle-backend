package com.rentle.shared.event;

import java.util.UUID;

public record ReviewCreatedEvent(
        UUID reviewId,
        UUID subjectUserId,
        int rating
) {}
