package com.rentle.domain.review.service;

import com.rentle.config.RentleProperties;
import com.rentle.domain.booking.model.Booking;
import com.rentle.domain.booking.model.BookingStatus;
import com.rentle.domain.booking.repository.BookingRepository;
import com.rentle.domain.review.dto.CreateReviewRequest;
import com.rentle.domain.review.dto.ReviewResponse;
import com.rentle.domain.review.model.Review;
import com.rentle.domain.review.repository.ReviewRepository;
import com.rentle.domain.user.model.User;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.event.ReviewCreatedEvent;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.exception.UnauthorizedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final RentleProperties props;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewService(ReviewRepository reviewRepository,
                         BookingRepository bookingRepository,
                         RentleProperties props,
                         ApplicationEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.props = props;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ReviewResponse create(UUID authorId, CreateReviewRequest req) {
        Booking booking = bookingRepository.findById(req.bookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        boolean isRenter = booking.getRenter().getId().equals(authorId);
        boolean isOwner = booking.getListing().getOwner().getId().equals(authorId);
        if (!isRenter && !isOwner) {
            throw new UnauthorizedException("Only booking participants can leave a review");
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new RentleException("Reviews are only allowed after the booking is completed");
        }
        // updated_at is the completion timestamp — the DB trigger enforces the same window
        Instant completedAt = booking.getUpdatedAt();
        if (Instant.now().isAfter(completedAt.plus(Duration.ofDays(props.reviewWindowDays())))) {
            throw new RentleException("Review window has expired ("
                    + props.reviewWindowDays() + " days after completion)");
        }
        if (reviewRepository.existsByBookingIdAndAuthorId(booking.getId(), authorId)) {
            throw new RentleException("You have already reviewed this booking");
        }

        User author = isRenter ? booking.getRenter() : booking.getListing().getOwner();
        User subject = isRenter ? booking.getListing().getOwner() : booking.getRenter();

        Review review = new Review();
        review.setBooking(booking);
        review.setAuthor(author);
        review.setSubject(subject);
        review.setListing(booking.getListing());
        review.setRating(req.rating());
        review.setComment(req.comment());
        review = reviewRepository.save(review);

        eventPublisher.publishEvent(new ReviewCreatedEvent(review.getId(), subject.getId(), req.rating()));
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> forListing(UUID listingId, Pageable pageable) {
        return PageResponse.from(
                reviewRepository.findByListingIdOrderByCreatedAtDesc(listingId, pageable),
                ReviewResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> aboutUser(UUID subjectId, Pageable pageable) {
        return PageResponse.from(
                reviewRepository.findBySubjectIdOrderByCreatedAtDesc(subjectId, pageable),
                ReviewResponse::from);
    }
}
