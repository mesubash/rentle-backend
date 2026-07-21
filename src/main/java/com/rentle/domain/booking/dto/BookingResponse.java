package com.rentle.domain.booking.dto;

import com.rentle.domain.booking.model.Booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID listingId,
        String listingTitle,
        String listingType,
        String coverImage,
        UUID ownerId,
        String ownerName,
        UUID renterId,
        String renterName,
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        BigDecimal totalPrice,
        BigDecimal depositAmount,
        boolean depositPaid,
        String depositProofUrl,
        String ownerPaymentWallet,
        String renterNote,
        String agreedTerms,
        java.util.Map<String, Object> attributes,
        boolean hasCheckoutCondition,
        String checkoutNote,
        boolean hasReturnCondition,
        String returnNote,
        BigDecimal platformFeeAmount,
        boolean feeInvoiced,
        java.util.List<com.rentle.domain.pricing.model.CancellationTier> cancellationSchedule,
        UUID assignedWorkerId,
        String assignedWorkerName,
        String cancellationReason,
        Instant createdAt
) {
    public static BookingResponse from(Booking b) {
        return from(b, null);
    }

    /** @param coverImage first listing image, resolved by the caller (the DTO has no repository). */
    public static BookingResponse from(Booking b, String coverImage) {
        return new BookingResponse(
                b.getId(),
                b.getListing().getId(),
                b.getListing().getTitle(),
                b.getListing().getType().name(),
                coverImage,
                b.getListing().getOwner().getId(),
                b.getListing().getOwner().getFullName(),
                b.getRenter().getId(),
                b.getRenter().getFullName(),
                b.getStartDate(),
                b.getEndDate(),
                b.getStartTime(),
                b.getEndTime(),
                b.getStatus().name(),
                b.getTotalPrice(),
                b.getDepositAmount(),
                Boolean.TRUE.equals(b.getDepositPaid()),
                b.getDepositProofUrl(),
                b.getListing().getOwner().getPaymentWallet(),
                b.getRenterNote(),
                b.getAgreedTerms(),
                b.getAttributes(),
                b.getCheckoutConditionRef() != null,
                b.getCheckoutNote(),
                b.getReturnConditionRef() != null,
                b.getReturnNote(),
                b.getPlatformFeeAmount(),
                b.isFeeInvoiced(),
                b.getCancellationSchedule(),
                b.getAssignedWorkerId(),
                b.getAssignedWorkerName(),
                b.getCancellationReason(),
                b.getCreatedAt()
        );
    }
}
