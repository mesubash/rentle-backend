package com.rentle.domain.messaging.repository;

import com.rentle.domain.messaging.dto.ThreadSummary;
import com.rentle.domain.messaging.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByBookingIdOrderByCreatedAtAsc(UUID bookingId, Pageable pageable);

    /** One row per booking the user has messages in: latest activity + their unread count. */
    @Query("""
        SELECT new com.rentle.domain.messaging.dto.ThreadSummary(
            m.booking.id,
            MAX(m.createdAt),
            SUM(CASE WHEN m.isRead = false AND m.sender.id <> :userId THEN 1L ELSE 0L END))
        FROM Message m
        WHERE m.booking.renter.id = :userId OR m.booking.listing.owner.id = :userId
        GROUP BY m.booking.id
    """)
    List<ThreadSummary> threadSummaries(@Param("userId") UUID userId);

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
