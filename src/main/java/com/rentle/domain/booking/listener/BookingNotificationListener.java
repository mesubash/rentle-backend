package com.rentle.domain.booking.listener;

import com.rentle.shared.event.BookingApprovedEvent;
import com.rentle.shared.event.BookingCompletedEvent;
import com.rentle.shared.event.BookingCreatedEvent;
import com.rentle.shared.event.BookingDepositConfirmedEvent;
import com.rentle.shared.event.BookingRejectedEvent;
import com.rentle.shared.notification.SmsService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingNotificationListener {

    private final SmsService smsService;

    public BookingNotificationListener(SmsService smsService) {
        this.smsService = smsService;
    }

    @Async
    @TransactionalEventListener
    public void onBookingCreated(BookingCreatedEvent event) {
        smsService.send(event.ownerPhone(),
                "You have a new booking request for '%s'. Open Rentle to approve.".formatted(event.listingTitle()));
    }

    @Async
    @TransactionalEventListener
    public void onBookingApproved(BookingApprovedEvent event) {
        smsService.send(event.renterPhone(),
                "Your booking for '%s' was approved. Send the deposit and upload the proof.".formatted(event.listingTitle()));
    }

    @Async
    @TransactionalEventListener
    public void onBookingRejected(BookingRejectedEvent event) {
        smsService.send(event.renterPhone(),
                "Your booking request for '%s' was declined.".formatted(event.listingTitle()));
    }

    @Async
    @TransactionalEventListener
    public void onDepositConfirmed(BookingDepositConfirmedEvent event) {
        smsService.send(event.renterPhone(),
                "Deposit confirmed for '%s'. Your booking is now active.".formatted(event.listingTitle()));
    }

    @Async
    @TransactionalEventListener
    public void onBookingCompleted(BookingCompletedEvent event) {
        String message = "Booking for '%s' is complete. Leave a review on Rentle within 30 days."
                .formatted(event.listingTitle());
        smsService.send(event.ownerPhone(), message);
        smsService.send(event.renterPhone(), message);
    }
}
