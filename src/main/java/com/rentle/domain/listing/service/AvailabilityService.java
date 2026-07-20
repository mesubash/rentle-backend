package com.rentle.domain.listing.service;

import com.rentle.domain.booking.model.Booking;
import com.rentle.domain.booking.model.BookingStatus;
import com.rentle.domain.booking.repository.BookingRepository;
import com.rentle.domain.listing.dto.AvailabilityResponse;
import com.rentle.domain.listing.dto.BlockDatesRequest;
import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.PriceUnit;
import com.rentle.domain.listing.model.UnavailableRange;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.domain.listing.repository.UnavailableRangeRepository;
import com.rentle.shared.exception.BookingConflictException;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AvailabilityService {

    // Statuses that do NOT occupy the calendar. REQUESTED is here (P1-27): a pending
    // request does not hold dates, so it is neither shown as BOOKED nor treated as a conflict.
    private static final Set<BookingStatus> INACTIVE_STATUSES =
            Set.of(BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.REQUESTED);

    private final UnavailableRangeRepository unavailableRangeRepository;
    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;

    public AvailabilityService(UnavailableRangeRepository unavailableRangeRepository,
                               BookingRepository bookingRepository,
                               ListingRepository listingRepository) {
        this.unavailableRangeRepository = unavailableRangeRepository;
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
    }

    /**
     * Pre-check before booking creation; the GiST exclusion constraint is the final
     * guard. For hourly listings the friendly date-granular booking pre-check would
     * wrongly reject non-overlapping same-day slots, so it is skipped there and the
     * time-aware DB constraint handles conflicts (surfaced as a 409). Owner-blocked
     * ranges are always whole-day and always checked.
     */
    @Transactional(readOnly = true)
    public void assertAvailable(UUID listingId, PriceUnit priceUnit,
                                LocalDate startDate, LocalDate endDate) {
        if (priceUnit != PriceUnit.PER_HOUR
                && bookingRepository.existsOverlap(listingId, startDate, endDate)) {
            throw new BookingConflictException("Listing is already booked for the selected dates");
        }
        if (unavailableRangeRepository.existsOverlap(listingId, startDate, endDate)) {
            throw new BookingConflictException("Owner has blocked the selected dates");
        }
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID listingId) {
        if (!listingRepository.existsById(listingId)) {
            throw new ResourceNotFoundException("Listing not found");
        }
        List<AvailabilityResponse.BlockedRange> blocked = new ArrayList<>();

        for (UnavailableRange r : unavailableRangeRepository.findByListingIdOrderByStartDateAsc(listingId)) {
            blocked.add(new AvailabilityResponse.BlockedRange(
                    r.getId(), r.getStartDate(), r.getEndDate(), "OWNER_BLOCKED"));
        }
        for (Booking b : bookingRepository.findByListingIdAndStatusNotInAndEndDateGreaterThanEqual(
                listingId, INACTIVE_STATUSES, LocalDate.now())) {
            blocked.add(new AvailabilityResponse.BlockedRange(
                    null, b.getStartDate(), b.getEndDate(), "BOOKED"));
        }
        return new AvailabilityResponse(listingId, blocked);
    }

    @Transactional
    public AvailabilityResponse blockDates(UUID ownerId, UUID listingId, BlockDatesRequest req) {
        Listing listing = getOwnedListing(ownerId, listingId);
        if (req.endDate().isBefore(req.startDate())) {
            throw new RentleException("End date must be on or after start date");
        }
        UnavailableRange range = new UnavailableRange();
        range.setListing(listing);
        range.setStartDate(req.startDate());
        range.setEndDate(req.endDate());
        range.setReason(req.reason());
        unavailableRangeRepository.save(range);
        return getAvailability(listingId);
    }

    @Transactional
    public void unblockDates(UUID ownerId, UUID listingId, UUID rangeId) {
        getOwnedListing(ownerId, listingId);
        UnavailableRange range = unavailableRangeRepository.findById(rangeId)
                .orElseThrow(() -> new ResourceNotFoundException("Blocked range not found"));
        if (!range.getListing().getId().equals(listingId)) {
            throw new ResourceNotFoundException("Blocked range not found");
        }
        unavailableRangeRepository.delete(range);
    }

    private Listing getOwnedListing(UUID ownerId, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        if (!listing.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Only the listing owner can manage availability");
        }
        return listing;
    }
}
