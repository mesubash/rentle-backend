package com.rentle.domain.messaging.service;

import com.rentle.domain.booking.model.Booking;
import com.rentle.domain.booking.model.BookingStatus;
import com.rentle.domain.booking.repository.BookingRepository;
import com.rentle.domain.messaging.dto.MessageResponse;
import com.rentle.domain.messaging.dto.SendMessageRequest;
import com.rentle.domain.messaging.model.Message;
import com.rentle.domain.messaging.repository.MessageRepository;
import com.rentle.domain.user.model.User;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.exception.UnauthorizedException;
import com.rentle.shared.notification.SmsService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class MessageService {

    /** Thread opens on approval; REQUESTED/REJECTED/CANCELLED bookings can't message. */
    private static final Set<BookingStatus> MESSAGEABLE_STATUSES = Set.of(
            BookingStatus.APPROVED, BookingStatus.DEPOSIT_PENDING,
            BookingStatus.ACTIVE, BookingStatus.COMPLETED);

    private final MessageRepository messageRepository;
    private final BookingRepository bookingRepository;
    private final SmsService smsService;

    public MessageService(MessageRepository messageRepository,
                          BookingRepository bookingRepository,
                          SmsService smsService) {
        this.messageRepository = messageRepository;
        this.bookingRepository = bookingRepository;
        this.smsService = smsService;
    }

    @Transactional
    public MessageResponse send(UUID senderId, UUID bookingId, SendMessageRequest req) {
        Booking booking = getBookingForParticipant(bookingId, senderId);
        if (!MESSAGEABLE_STATUSES.contains(booking.getStatus())) {
            throw new RentleException("Messaging opens once the booking is approved");
        }

        boolean firstMessage = messageRepository.countByBookingId(bookingId) == 0;

        User sender = booking.getRenter().getId().equals(senderId)
                ? booking.getRenter()
                : booking.getListing().getOwner();
        User recipient = booking.getRenter().getId().equals(senderId)
                ? booking.getListing().getOwner()
                : booking.getRenter();

        Message message = new Message();
        message.setBooking(booking);
        message.setSender(sender);
        message.setContent(req.content());
        message = messageRepository.save(message);

        // SMS only on the first message of a booking, to avoid spam
        if (firstMessage) {
            smsService.send(recipient.getPhoneNumber(),
                    "New message about '%s' on Rentle.".formatted(booking.getListing().getTitle()));
        }
        return MessageResponse.from(message);
    }

    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> getMessages(UUID actorId, UUID bookingId, Pageable pageable) {
        getBookingForParticipant(bookingId, actorId);
        return PageResponse.from(
                messageRepository.findByBookingIdOrderByCreatedAtAsc(bookingId, pageable),
                MessageResponse::from);
    }

    @Transactional
    public int markAllRead(UUID actorId, UUID bookingId) {
        getBookingForParticipant(bookingId, actorId);
        return messageRepository.markAllRead(bookingId, actorId, Instant.now());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return messageRepository.countUnreadForUser(userId);
    }

    private Booking getBookingForParticipant(UUID bookingId, UUID actorId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        boolean isRenter = booking.getRenter().getId().equals(actorId);
        boolean isOwner = booking.getListing().getOwner().getId().equals(actorId);
        if (!isRenter && !isOwner) {
            throw new UnauthorizedException("You are not a participant of this booking");
        }
        return booking;
    }
}
