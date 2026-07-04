package com.rentle.unit;

import com.rentle.domain.booking.model.BookingStatus;
import com.rentle.domain.booking.service.BookingStateMachine;
import com.rentle.shared.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.rentle.domain.booking.model.BookingStatus.ACTIVE;
import static com.rentle.domain.booking.model.BookingStatus.APPROVED;
import static com.rentle.domain.booking.model.BookingStatus.CANCELLED;
import static com.rentle.domain.booking.model.BookingStatus.COMPLETED;
import static com.rentle.domain.booking.model.BookingStatus.DEPOSIT_PENDING;
import static com.rentle.domain.booking.model.BookingStatus.REJECTED;
import static com.rentle.domain.booking.model.BookingStatus.REQUESTED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingStateMachineTest {

    private final BookingStateMachine stateMachine = new BookingStateMachine();

    @ParameterizedTest
    @CsvSource({
            "REQUESTED, APPROVED",
            "REQUESTED, REJECTED",
            "REQUESTED, CANCELLED",
            "APPROVED, DEPOSIT_PENDING",
            "APPROVED, CANCELLED",
            "DEPOSIT_PENDING, ACTIVE",
            "DEPOSIT_PENDING, CANCELLED",
            "ACTIVE, COMPLETED",
            "ACTIVE, CANCELLED",
    })
    void allowsValidTransitions(BookingStatus from, BookingStatus to) {
        assertDoesNotThrow(() -> stateMachine.assertValidTransition(from, to));
    }

    @ParameterizedTest
    @CsvSource({
            "REQUESTED, ACTIVE",
            "REQUESTED, COMPLETED",
            "APPROVED, ACTIVE",
            "APPROVED, COMPLETED",
            "APPROVED, REJECTED",
            "DEPOSIT_PENDING, COMPLETED",
            "DEPOSIT_PENDING, REQUESTED",
            "ACTIVE, REQUESTED",
            "ACTIVE, DEPOSIT_PENDING",
    })
    void rejectsInvalidTransitions(BookingStatus from, BookingStatus to) {
        assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.assertValidTransition(from, to));
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (BookingStatus terminal : new BookingStatus[]{COMPLETED, CANCELLED, REJECTED}) {
            for (BookingStatus to : BookingStatus.values()) {
                assertThrows(InvalidStateTransitionException.class,
                        () -> stateMachine.assertValidTransition(terminal, to),
                        terminal + " -> " + to + " must be rejected");
            }
        }
    }
}
