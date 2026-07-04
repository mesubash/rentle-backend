package com.rentle.domain.review.repository;

import com.rentle.domain.review.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Page<Review> findByListingIdOrderByCreatedAtDesc(UUID listingId, Pageable pageable);

    Page<Review> findBySubjectIdOrderByCreatedAtDesc(UUID subjectId, Pageable pageable);

    boolean existsByBookingIdAndAuthorId(UUID bookingId, UUID authorId);
}
