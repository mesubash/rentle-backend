package com.rentle.domain.booking.service;

import com.rentle.domain.booking.model.BookingStatus;
import com.rentle.shared.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static com.rentle.domain.booking.model.BookingStatus.ACTIVE;
import static com.rentle.domain.booking.model.BookingStatus.APPROVED;
import static com.rentle.domain.booking.model.BookingStatus.CANCELLED;
import static com.rentle.domain.booking.model.BookingStatus.COMPLETED;
import static com.rentle.domain.booking.model.BookingStatus.DEPOSIT_PENDING;
import static com.rentle.domain.booking.model.BookingStatus.REJECTED;
import static com.rentle.domain.booking.model.BookingStatus.REQUESTED;

@Component
public class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> VALID_TRANSITIONS =
        Map.of(
            REQUESTED,       Set.of(APPROVED, REJECTED, CANCELLED),
            APPROVED,        Set.of(DEPOSIT_PENDING, CANCELLED),
            DEPOSIT_PENDING, Set.of(ACTIVE, CANCELLED),
            ACTIVE,          Set.of(COMPLETED, CANCELLED),
            COMPLETED,       Set.of(),
            CANCELLED,       Set.of(),
            REJECTED,        Set.of()
        );

    public void assertValidTransition(BookingStatus from, BookingStatus to) {
        if (!VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidStateTransitionException(
                "Cannot transition booking from %s to %s".formatted(from, to)
            );
        }
    }
}
