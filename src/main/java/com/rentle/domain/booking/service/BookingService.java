package com.rentle.domain.booking.service;

import com.rentle.domain.booking.dto.BookingResponse;
import com.rentle.domain.booking.dto.CreateBookingRequest;
import com.rentle.domain.booking.model.Booking;
import com.rentle.domain.booking.model.BookingStatus;
import com.rentle.domain.booking.repository.BookingRepository;
import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.ListingStatus;
import com.rentle.domain.listing.model.ListingType;
import com.rentle.domain.listing.model.PriceUnit;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.domain.listing.repository.ProductDetailRepository;
import com.rentle.domain.listing.repository.ServiceDetailRepository;
import com.rentle.domain.listing.service.AvailabilityService;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.event.BookingApprovedEvent;
import com.rentle.shared.event.BookingCompletedEvent;
import com.rentle.shared.event.BookingCreatedEvent;
import com.rentle.shared.event.BookingDepositConfirmedEvent;
import com.rentle.shared.event.BookingRejectedEvent;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.exception.UnauthorizedException;
import com.rentle.shared.storage.ImageValidator;
import com.rentle.shared.storage.StorageService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional
public class BookingService {

    private static final long DEPOSIT_PROOF_MAX_BYTES = 5L * 1024 * 1024;

    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final ProductDetailRepository productDetailRepository;
    private final ServiceDetailRepository serviceDetailRepository;
    private final UserRepository userRepository;
    private final AvailabilityService availabilityService;
    private final PricingService pricingService;
    private final BookingStateMachine stateMachine;
    private final StorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(BookingRepository bookingRepository,
                          ListingRepository listingRepository,
                          ProductDetailRepository productDetailRepository,
                          ServiceDetailRepository serviceDetailRepository,
                          UserRepository userRepository,
                          AvailabilityService availabilityService,
                          PricingService pricingService,
                          BookingStateMachine stateMachine,
                          StorageService storageService,
                          ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
        this.productDetailRepository = productDetailRepository;
        this.serviceDetailRepository = serviceDetailRepository;
        this.userRepository = userRepository;
        this.availabilityService = availabilityService;
        this.pricingService = pricingService;
        this.stateMachine = stateMachine;
        this.storageService = storageService;
        this.eventPublisher = eventPublisher;
    }

    public BookingResponse createBooking(UUID renterId, CreateBookingRequest req) {
        // KYC-first: only fully verified users (phone + email + ID, i.e. VERIFIED)
        // can transact. Verification status carries the transitive proof.
        User renter = userRepository.findById(renterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (renter.getStatus() != UserStatus.VERIFIED) {
            throw new UnauthorizedException(
                    "Complete verification (phone, email, and ID) before booking");
        }

        Listing listing = listingRepository.findByIdAndStatus(req.listingId(), ListingStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found or inactive"));

        // Self-booking guard (also enforced by DB trigger)
        if (listing.getOwner().getId().equals(renterId)) {
            throw new RentleException("You cannot book your own listing");
        }

        validateDates(listing, req);

        // Availability pre-check (GiST exclusion constraint is the final guard)
        availabilityService.assertAvailable(
                req.listingId(), listing.getPriceUnit(), req.startDate(), req.endDate());

        BigDecimal price = pricingService.calculate(
                listing, req.startDate(), req.endDate(), req.startTime(), req.endTime());

        Booking booking = new Booking();
        booking.setListing(listing);
        booking.setRenter(renter);
        booking.setStartDate(req.startDate());
        booking.setEndDate(req.endDate());
        booking.setStartTime(req.startTime());
        booking.setEndTime(req.endTime());
        booking.setTotalPrice(price);
        booking.setDepositAmount(listing.getDepositAmount());
        booking.setRenterNote(req.note());
        booking = bookingRepository.save(booking);

        eventPublisher.publishEvent(new BookingCreatedEvent(
                booking.getId(), listing.getTitle(), listing.getOwner().getPhoneNumber()));
        return BookingResponse.from(booking);
    }

    public BookingResponse approve(UUID ownerId, UUID bookingId) {
        Booking booking = getBookingForOwner(bookingId, ownerId);
        stateMachine.assertValidTransition(booking.getStatus(), BookingStatus.APPROVED);
        booking.setStatus(BookingStatus.APPROVED);
        booking = bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingApprovedEvent(
                booking.getId(), booking.getListing().getTitle(), booking.getRenter().getPhoneNumber()));
        return BookingResponse.from(booking);
    }

    public BookingResponse reject(UUID ownerId, UUID bookingId, String reason) {
        Booking booking = getBookingForOwner(bookingId, ownerId);
        stateMachine.assertValidTransition(booking.getStatus(), BookingStatus.REJECTED);
        booking.setStatus(BookingStatus.REJECTED);
        booking.setCancellationReason(reason);
        booking = bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingRejectedEvent(
                booking.getId(), booking.getListing().getTitle(), booking.getRenter().getPhoneNumber()));
        return BookingResponse.from(booking);
    }

    public BookingResponse uploadDepositProof(UUID renterId, UUID bookingId, MultipartFile file) {
        Booking booking = getBooking(bookingId);
        if (!booking.getRenter().getId().equals(renterId)) {
            throw new UnauthorizedException("Only the renter can upload deposit proof");
        }
        if (booking.getStatus() != BookingStatus.APPROVED
                && booking.getStatus() != BookingStatus.DEPOSIT_PENDING) {
            throw new RentleException("Deposit proof can only be uploaded after approval");
        }
        ImageValidator.validate(file, DEPOSIT_PROOF_MAX_BYTES);
        booking.setDepositProofUrl(storageService.upload(file, "deposits/" + bookingId));
        if (booking.getStatus() == BookingStatus.APPROVED) {
            booking.setStatus(BookingStatus.DEPOSIT_PENDING);
        }
        return BookingResponse.from(bookingRepository.save(booking));
    }

    public BookingResponse confirmDeposit(UUID ownerId, UUID bookingId) {
        Booking booking = getBookingForOwner(bookingId, ownerId);

        // Zero-deposit listings skip proof upload; step through DEPOSIT_PENDING
        // so the DB transition trigger sees each valid hop.
        if (booking.getStatus() == BookingStatus.APPROVED
                && booking.getDepositAmount().compareTo(BigDecimal.ZERO) == 0) {
            booking.setStatus(BookingStatus.DEPOSIT_PENDING);
            bookingRepository.saveAndFlush(booking);
        }

        stateMachine.assertValidTransition(booking.getStatus(), BookingStatus.ACTIVE);
        booking.setStatus(BookingStatus.ACTIVE);
        booking.setDepositPaid(true);
        booking = bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingDepositConfirmedEvent(
                booking.getId(), booking.getListing().getTitle(), booking.getRenter().getPhoneNumber()));
        return BookingResponse.from(booking);
    }

    public BookingResponse complete(UUID actorId, UUID bookingId) {
        Booking booking = getBookingForParticipant(bookingId, actorId);
        stateMachine.assertValidTransition(booking.getStatus(), BookingStatus.COMPLETED);
        booking.setStatus(BookingStatus.COMPLETED);
        booking = bookingRepository.save(booking);

        Listing listing = booking.getListing();
        listing.setTotalBookings(listing.getTotalBookings() + 1);
        listingRepository.save(listing);

        eventPublisher.publishEvent(new BookingCompletedEvent(
                booking.getId(),
                listing.getTitle(),
                listing.getOwner().getPhoneNumber(),
                booking.getRenter().getPhoneNumber()));
        return BookingResponse.from(booking);
    }

    public BookingResponse cancel(UUID actorId, UUID bookingId, String reason) {
        Booking booking = getBookingForParticipant(bookingId, actorId);
        stateMachine.assertValidTransition(booking.getStatus(), BookingStatus.CANCELLED);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason);
        booking.setCancelledAt(Instant.now());
        booking.setCancelledBy(actorId);
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public BookingResponse getDetail(UUID actorId, UUID bookingId) {
        return BookingResponse.from(getBookingForParticipant(bookingId, actorId));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookingsAsRenter(UUID renterId, Pageable pageable) {
        return PageResponse.from(bookingRepository.findByRenter(renterId, pageable), BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookingsAsOwner(UUID ownerId, Pageable pageable) {
        return PageResponse.from(bookingRepository.findByOwner(ownerId, pageable), BookingResponse::from);
    }

    private void validateDates(Listing listing, CreateBookingRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new RentleException("End date must be on or after start date");
        }
        if (req.startDate().isBefore(LocalDate.now())) {
            throw new RentleException("Start date cannot be in the past");
        }
        // Hourly bookings are a single day's time window; a multi-day span would be
        // mispriced and ambiguous, so require one day and both times.
        if (listing.getPriceUnit() == PriceUnit.PER_HOUR) {
            if (!req.startDate().equals(req.endDate())) {
                throw new RentleException("Hourly bookings must start and end on the same day");
            }
            if (req.startTime() == null || req.endTime() == null) {
                throw new RentleException("Start and end time are required for hourly listings");
            }
            if (!req.endTime().isAfter(req.startTime())) {
                throw new RentleException("End time must be after start time");
            }
        }

        if (listing.getType() == ListingType.PRODUCT) {
            productDetailRepository.findByListingId(listing.getId()).ifPresent(detail -> {
                long days = ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1;
                if (days < detail.getMinRentalDays()) {
                    throw new RentleException("Minimum rental period is " + detail.getMinRentalDays() + " days");
                }
                if (detail.getMaxRentalDays() != null && days > detail.getMaxRentalDays()) {
                    throw new RentleException("Maximum rental period is " + detail.getMaxRentalDays() + " days");
                }
            });
        } else {
            serviceDetailRepository.findByListingId(listing.getId()).ifPresent(detail -> {
                LocalTime start = req.startTime() != null ? req.startTime() : LocalTime.MIDNIGHT;
                LocalDateTime serviceStart = LocalDateTime.of(req.startDate(), start);
                if (serviceStart.isBefore(LocalDateTime.now().plusHours(detail.getMinNoticeHours()))) {
                    throw new RentleException(
                            "This service requires " + detail.getMinNoticeHours() + " hours advance notice");
                }
            });
        }
    }

    private Booking getBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private Booking getBookingForOwner(UUID bookingId, UUID ownerId) {
        Booking booking = getBooking(bookingId);
        if (!booking.getListing().getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Only the listing owner can perform this action");
        }
        return booking;
    }

    private Booking getBookingForParticipant(UUID bookingId, UUID actorId) {
        Booking booking = getBooking(bookingId);
        boolean isRenter = booking.getRenter().getId().equals(actorId);
        boolean isOwner = booking.getListing().getOwner().getId().equals(actorId);
        if (!isRenter && !isOwner) {
            throw new UnauthorizedException("You are not a participant of this booking");
        }
        return booking;
    }
}
