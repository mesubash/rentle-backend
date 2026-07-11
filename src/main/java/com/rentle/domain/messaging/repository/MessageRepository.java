package com.rentle.domain.messaging.repository;

import com.rentle.domain.messaging.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByBookingIdOrderByCreatedAtAsc(UUID bookingId, Pageable pageable);

    long countByBookingId(UUID bookingId);

    /** Unread messages across every booking the user participates in (sent by the other party). */
    @Query("""
        SELECT COUNT(m) FROM Message m
        WHERE m.isRead = false
        AND m.sender.id <> :userId
        AND (m.booking.renter.id = :userId OR m.booking.listing.owner.id = :userId)
    """)
    long countUnreadForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("""
        UPDATE Message m SET m.isRead = true, m.readAt = :now
        WHERE m.booking.id = :bookingId
        AND m.sender.id <> :readerId
        AND m.isRead = false
    """)
    int markAllRead(@Param("bookingId") UUID bookingId,
                    @Param("readerId") UUID readerId,
                    @Param("now") Instant now);
}
