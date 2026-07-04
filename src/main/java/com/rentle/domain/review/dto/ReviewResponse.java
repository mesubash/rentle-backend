package com.rentle.domain.review.dto;

import com.rentle.domain.review.model.Review;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID bookingId,
        UUID authorId,
        String authorName,
        UUID subjectId,
        UUID listingId,
        int rating,
        String comment,
        Instant createdAt
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getBooking().getId(),
                r.getAuthor().getId(),
                r.getAuthor().getFullName(),
                r.getSubject().getId(),
                r.getListing().getId(),
                r.getRating(),
                r.getComment(),
                r.getCreatedAt()
        );
    }
}
