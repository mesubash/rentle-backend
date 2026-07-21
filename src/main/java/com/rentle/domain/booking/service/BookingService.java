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
import com.rentle.domain.listing.repository.ListingImageRepository;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.domain.listing.repository.ProductDetailRepository;
import com.rentle.domain.listing.repository.ServiceDetailRepository;
import com.rentle.domain.listing.service.AvailabilityService;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.event.BookingApprovedEvent;
import com.rentle.shared.event.BookingCancelledEvent;
import com.rentle.shared.event.BookingCompletedEvent;
import com.rentle.shared.event.BookingCreatedEvent;
import com.rentle.shared.event.BookingDepositConfirmedEvent;
import com.rentle.shared.event.BookingRejectedEvent;
import com.rentle.shared.exception.BookingConflictException;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.exception.UnauthorizedException;
import com.rentle.shared.storage.ImageValidator;
import com.rentle.shared.storage.PrivateStorageService;
import com.rentle.shared.storage.StorageService;
import org.springframework.core.io.Resource;
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
    private static final int MAX_OPEN_REQUESTS = 15;   // ponytail: fixed cap, per-listing cap if abuse appears

    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final ListingImageRepository listingImageRepository;
    private final ProductDetailRepository productDetailRepository;
    private final ServiceDetailRepository serviceDetailRepository;
    private final UserRepository userRepository;
    private final AvailabilityService availabilityService;
    private final PricingService pricingService;
    private final BookingStateMachine stateMachine;
    private final StorageService storageService;
    private final PrivateStorageService privateStorage;
    private final com.rentle.domain.notification.service.NotificationService notificationService;
    private final com.rentle.domain.platform.settings.PlatformSettingsService platformSettings;
    private final com.rentle.domain.template.service.FieldTemplateService templateService;
    private final com.rentle.domain.pricing.service.PricingPolicyService pricingPolicyService;
    private final com.rentle.domain.business.repository.WorkerRepository workerRepository;
    private final com.rentle.domain.organization.service.OrganizationService organizationService;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(BookingRepository bookingRepository,
                          ListingRepository listingRepository,
                          ListingImageRepository listingImageRepository,
                          ProductDetailRepository productDetailRepository,
                          ServiceDetailRepository serviceDetailRepository,
                          UserRepository userRepository,
                          AvailabilityService availabilityService,
                          PricingService pricingService,
                          BookingStateMachine stateMachine,
                          StorageService storageService,
                          PrivateStorageService privateStorage,
                          com.rentle.domain.notification.service.NotificationService notificationService,
                          com.rentle.domain.platform.settings.PlatformSettingsService platformSettings,
                          com.rentle.domain.template.service.FieldTemplateService templateService,
                          com.rentle.domain.pricing.service.PricingPolicyService pricingPolicyService,
                          com.rentle.domain.business.repository.WorkerRepository workerRepository,
                          com.rentle.domain.organization.service.OrganizationService organizationService,
                          ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
        this.listingImageRepository = listingImageRepository;
        this.productDetailRepository = productDetailRepository;
        this.serviceDetailRepository = serviceDetailRepository;
        this.userRepository = userRepository;
        this.availabilityService = availabilityService;
        this.pricingService = pricingService;
        this.stateMachine = stateMachine;
        this.storageService = storageService;
        this.privateStorage = privateStorage;
        this.notificationService = notificationService;
        this.platformSettings = platformSettings;
        this.templateService = templateService;
        this.pricingPolicyService = pricingPolicyService;
        this.workerRepository = workerRepository;
        this.organizationService = organizationService;
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

        // Paused/hidden category (docs/12): no new bookings; existing ones still complete.
        if (!listing.getCategory().getIsActive()) {
            throw new RentleException("This category is not currently accepting bookings");
        }

        // Self-booking guard (also enforced by DB trigger)
        if (listing.getOwner().getId().equals(renterId)) {
            throw new RentleException("You cannot book your own listing");
        }

        // Cap open (unapproved) requests per renter: requests are free and no longer hold
        // dates (P1-27), so bound how many a single user can have outstanding to deter spam.
        if (bookingRepository.countByRenterIdAndStatus(renterId, BookingStatus.REQUESTED) >= MAX_OPEN_REQUESTS) {
            throw new RentleException("You have too many pending booking requests. "
                    + "Wait for some to be answered before sending more.");
        }

        validateDates(listing, req);

        // Availability pre-check (GiST exclusion constraint is the final guard)
        availabilityService.assertAvailable(
                req.listingId(), listing.getPriceUnit(), req.startDate(), req.endDate());

        BigDecimal price = pricingService.calculate(
                listing, req.startDate(), req.endDate(), req.startTime(), req.endTime());

        Booking booking = new Booking();
        booking.setListing(listing);
        booking.setProviderOrgId(listing.getOrgId());   // org-owned listing → org is the provider
        booking.setRenter(renter);
        booking.setStartDate(req.startDate());
        booking.setEndDate(req.endDate());
        booking.setStartTime(req.startTime());
        booking.setEndTime(req.endTime());
        booking.setTotalPrice(price);
        booking.setDepositAmount(listing.getDepositAmount());
        booking.setRenterNote(req.note());
        booking.setAgreedTerms(listing.getRentalTerms());   // snapshot terms in force at request time
        // Validate + store this category's BOOKING template answers, if a template is defined.
        java.util.Map<String, Object> bookingAttrs = req.attributes() != null ? req.attributes() : new java.util.HashMap<>();
        var bookingTpl = templateService.current(
                listing.getCategory().getId(), com.rentle.domain.template.model.TemplateScope.BOOKING);
        bookingTpl.ifPresent(tpl -> templateService.validateAnswers(tpl.getFields(), bookingAttrs));
        booking.setAttributes(bookingAttrs);
        booking.setAttributesTemplateVersion(bookingTpl.map(t -> t.getVersion()).orElse(null));
        // Snapshot the category's cancellation schedule in force now (docs/13).
        booking.setCancellationSchedule(pricingPolicyService.cancellationTiers(listing.getCategory().getId()));
        booking = bookingRepository.save(booking);

        eventPublisher.publishEvent(new BookingCreatedEvent(
                booking.getId(), listing.getTitle(), listing.getOwner().getPhoneNumber()));
        notificationService.notify(listing.getOwner().getId(), "BOOKING_REQUESTED",
                "New booking request for '" + listing.getTitle() + "'.", "/bookings/" + booking.getId());
        return BookingResponse.from(booking);
    }

    /**
     * Owner adjusts the agreed price before money moves — for services where the real
     * scope (distance, hours, deliverables) differs from the listing's mechanical rate.
     * Allowed only while REQUESTED or APPROVED (before the deposit step), so the recorded
     * total reflects what was actually agreed rather than a fiction.
     */
    public BookingResponse adjustPrice(UUID ownerId, UUID bookingId, BigDecimal newTotal) {
        Booking booking = getBookingForOwner(bookingId, ownerId);
        if (booking.getStatus() != BookingStatus.REQUESTED && booking.getStatus() != BookingStatus.APPROVED) {
            throw new RentleException("Price can only be adjusted before the deposit step");
        }
        if (newTotal == null || newTotal.signum() < 0) {
            throw new RentleException("Price must be zero or more");
        }
        booking.setTotalPrice(newTotal);
        return BookingResponse.from(bookingRepository.save(booking));
    }

    /** An org member assigns which worker will attend this booking (docs/07 Phase B). Workers
     *  belong to the provider org, so only org listings can have an assigned worker. */
    public BookingResponse assignWorker(UUID ownerId, UUID bookingId, UUID workerId) {
        Booking booking = getBookingForOwner(bookingId, ownerId);
        if (workerId == null) {
            booking.setAssignedWorkerId(null);
            booking.setAssignedWorkerName(null);
        } else {
            UUID orgId = booking.getListing().getOrgId();
            if (orgId == null) {
                throw new RentleException("Only organization listings can assign a worker");
            }
            var worker = workerRepository.findByIdAndOrgId(workerId, orgId)
                    .orElseThrow(() -> new ResourceNotFoundException("Worker not found"));
            booking.setAssignedWorkerId(worker.getId());
            booking.setAssignedWorkerName(worker.getName());   // snapshot so the client sees who attends
        }
        return BookingResponse.from(bookingRepository.save(booking));
    }

    public BookingResponse approve(UUID ownerId, UUID bookingId) {
        Booking booking = getBookingForOwner(bookingId, ownerId);
        stateMachine.assertValidTransition(booking.getStatus(), BookingStatus.APPROVED);
        // Requests no longer hold dates (P1-27), so several may overlap. Approving is the
        // moment exclusivity is claimed: reject if another booking is already APPROVED+ over
        // these dates. existsOverlap counts only APPROVED+ (not this still-REQUESTED one).
        // Date-granular check is skipped for hourly listings; the GiST constraint is the
        // final guard there. ponytail: hourly approve-conflict surfaces as a DB 409, not a
        // pre-check — fine until hourly double-approve proves common.
        if (booking.getListing().getPriceUnit() != PriceUnit.PER_HOUR
                && bookingRepository.existsOverlap(
                        booking.getListing().getId(), booking.getStartDate(), booking.getEndDate())) {
            throw new BookingConflictException(
                    "Those dates were just booked by another approved request.");
        }
        booking.setStatus(BookingStatus.APPROVED);
        booking = bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingApprovedEvent(
                booking.getId(), booking.getListing().getTitle(), booking.getRenter().getPhoneNumber()));
        notificationService.notify(booking.getRenter().getId(), "BOOKING_APPROVED",
                "Your booking for '" + booking.getListing().getTitle() + "' was approved.",
                "/bookings/" + booking.getId());
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
        notificationService.notify(booking.getRenter().getId(), "BOOKING_REJECTED",
                "Your booking request for '" + booking.getListing().getTitle() + "' was declined.",
                "/bookings/" + booking.getId());
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
        // Payment screenshots are sensitive: private storage, streamed back only to
        // booking participants — never the public /files/** root.
        booking.setDepositProofUrl(privateStorage.store(file, "deposits", bookingId.toString()));
        if (booking.getStatus() == BookingStatus.APPROVED) {
            booking.setStatus(BookingStatus.DEPOSIT_PENDING);
        }
        return BookingResponse.from(bookingRepository.save(booking));
    }

    /**
     * Record hand-over ("CHECKOUT") or return ("RETURN") condition evidence — a photo plus
     * an optional note — stored privately. Either participant may record; useful from ACTIVE
     * (hand-over) through COMPLETED (return), so disputes have a documented condition trail.
     */
    public BookingResponse recordCondition(UUID actorId, UUID bookingId, String phase,
                                           MultipartFile file, String note) {
        Booking booking = getBookingForParticipant(bookingId, actorId);
        if (booking.getStatus() != BookingStatus.ACTIVE && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new RentleException("Condition can only be recorded on an active or completed booking");
        }
        ImageValidator.validate(file, DEPOSIT_PROOF_MAX_BYTES);
        boolean checkout = "CHECKOUT".equalsIgnoreCase(phase);
        boolean returnPhase = "RETURN".equalsIgnoreCase(phase);
        if (!checkout && !returnPhase) {
            throw new RentleException("Phase must be CHECKOUT or RETURN");
        }
        String ref = privateStorage.store(file, "conditions", bookingId + "-" + phase.toLowerCase());
        if (checkout) {
            booking.setCheckoutConditionRef(ref);
            booking.setCheckoutNote(note);
        } else {
            booking.setReturnConditionRef(ref);
            booking.setReturnNote(note);
        }
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public DepositProof loadCondition(UUID actorId, UUID bookingId, String phase) {
        Booking booking = getBookingForParticipant(bookingId, actorId);
        String ref = "CHECKOUT".equalsIgnoreCase(phase)
                ? booking.getCheckoutConditionRef() : booking.getReturnConditionRef();
        if (ref == null) {
            throw new ResourceNotFoundException("No condition photo recorded");
        }
        return new DepositProof(privateStorage.load(ref), privateStorage.contentType(ref));
    }

    public record DepositProof(Resource resource, String contentType) {}

    @Transactional(readOnly = true)
    public DepositProof loadDepositProof(UUID actorId, UUID bookingId) {
        Booking booking = getBookingForParticipant(bookingId, actorId);
        String ref = booking.getDepositProofUrl();
        if (ref == null || ref.startsWith("/") || ref.startsWith("http")) {
            throw new ResourceNotFoundException("No deposit proof uploaded");
        }
        return new DepositProof(privateStorage.load(ref), privateStorage.contentType(ref));
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
        notificationService.notify(booking.getRenter().getId(), "DEPOSIT_CONFIRMED",
                "Deposit confirmed for '" + booking.getListing().getTitle() + "'. Your booking is active.",
                "/bookings/" + booking.getId());
        return BookingResponse.from(booking);
    }

    public BookingResponse complete(UUID actorId, UUID bookingId) {
        Booking booking = getBookingForParticipant(bookingId, actorId);
        stateMachine.assertValidTransition(booking.getStatus(), BookingStatus.COMPLETED);
        // Completion is only meaningful once the rental has actually begun. This also
        // closes the trust-farming loop: a zero-deposit booking for a future date could
        // otherwise be created and instantly completed to mint a reviewable transaction.
        LocalDateTime rentalStart = LocalDateTime.of(
                booking.getStartDate(),
                booking.getStartTime() != null ? booking.getStartTime() : LocalTime.MIN);
        if (LocalDateTime.now().isBefore(rentalStart)) {
            throw new RentleException("A booking can only be completed after the rental period has started");
        }
        // Freeze the commission snapshot at completion (P0-3). 0% during free launch.
        BigDecimal feePercent = platformSettings.platformFeePercent();
        booking.setPlatformFeePercent(feePercent);
        booking.setPlatformFeeAmount(booking.getTotalPrice()
                .multiply(feePercent)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
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
        String completedMsg = "Booking for '" + listing.getTitle() + "' is complete. Leave a review.";
        notificationService.notify(listing.getOwner().getId(), "BOOKING_COMPLETED", completedMsg, "/bookings/" + booking.getId());
        notificationService.notify(booking.getRenter().getId(), "BOOKING_COMPLETED", completedMsg, "/bookings/" + booking.getId());
        return BookingResponse.from(booking);
    }

    public BookingResponse cancel(UUID actorId, UUID bookingId, String reason) {
        Booking booking = getBookingForParticipant(bookingId, actorId);
        stateMachine.assertValidTransition(booking.getStatus(), BookingStatus.CANCELLED);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason);
        booking.setCancelledAt(Instant.now());
        booking.setCancelledBy(actorId);
        booking = bookingRepository.save(booking);

        boolean cancelledByOwner = booking.getListing().getOwner().getId().equals(actorId);
        eventPublisher.publishEvent(new BookingCancelledEvent(
                booking.getId(),
                booking.getListing().getTitle(),
                booking.getListing().getOwner().getPhoneNumber(),
                booking.getRenter().getPhoneNumber(),
                cancelledByOwner));
        UUID counterparty = cancelledByOwner ? booking.getRenter().getId() : booking.getListing().getOwner().getId();
        notificationService.notify(counterparty, "BOOKING_CANCELLED",
                "A booking for '" + booking.getListing().getTitle() + "' was cancelled.",
                "/bookings/" + booking.getId());
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getDetail(UUID actorId, UUID bookingId) {
        Booking booking = getBookingForParticipant(bookingId, actorId);
        return BookingResponse.from(booking, coverFor(booking));
    }

    /** First image of a single booking's listing. */
    private String coverFor(Booking booking) {
        return coversFor(java.util.List.of(booking)).get(booking.getListing().getId());
    }

    /**
     * Cover image per listing id for a page of bookings, in one query.
     * Without this the client had to fetch the whole listing just to show a thumbnail.
     */
    private java.util.Map<UUID, String> coversFor(java.util.List<Booking> bookings) {
        java.util.List<UUID> listingIds = bookings.stream()
                .map(b -> b.getListing().getId()).distinct().toList();
        if (listingIds.isEmpty()) return java.util.Map.of();
        return listingImageRepository.findByListingIdInOrderBySortOrderAsc(listingIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        img -> img.getListing().getId(),
                        com.rentle.domain.listing.model.ListingImage::getUrl,
                        (first, second) -> first));
    }

    /** Map a page of bookings, resolving all cover images up front rather than per row. */
    private PageResponse<BookingResponse> toPageWithCovers(org.springframework.data.domain.Page<Booking> page) {
        java.util.Map<UUID, String> covers = coversFor(page.getContent());
        return PageResponse.from(page, b -> BookingResponse.from(b, covers.get(b.getListing().getId())));
    }

    /** Completed bookings and their platform-fee status, for the admin invoicing view (P0-3). */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listFees(boolean invoiced, Pageable pageable) {
        return PageResponse.from(
                bookingRepository.findByStatusAndFeeInvoiced(BookingStatus.COMPLETED, invoiced, pageable),
                BookingResponse::from);
    }

    /** Admin marks a booking's platform fee as invoiced (manual monthly collection). */
    @Transactional
    public BookingResponse markFeeInvoiced(UUID bookingId) {
        Booking booking = getBooking(bookingId);
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new RentleException("Only completed bookings carry a platform fee");
        }
        booking.setFeeInvoiced(true);
        booking.setFeeInvoicedAt(Instant.now());
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookingsAsRenter(UUID renterId, Pageable pageable) {
        return toPageWithCovers(bookingRepository.findByRenter(renterId, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookingsAsOwner(UUID ownerId, Pageable pageable) {
        return toPageWithCovers(bookingRepository.findByOwner(ownerId, pageable));
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
        if (!isProviderSide(booking, ownerId)) {
            throw new UnauthorizedException("Only the listing owner can perform this action");
        }
        return booking;
    }

    private Booking getBookingForParticipant(UUID bookingId, UUID actorId) {
        Booking booking = getBooking(bookingId);
        if (!booking.getRenter().getId().equals(actorId) && !isProviderSide(booking, actorId)) {
            throw new UnauthorizedException("You are not a participant of this booking");
        }
        return booking;
    }

    /** The provider side is the listing owner, or — for org listings — any member allowed to
     *  manage the org's bookings. Treats "acting as the org" the same as owning the listing. */
    private boolean isProviderSide(Booking booking, UUID userId) {
        if (booking.getListing().getOwner().getId().equals(userId)) return true;
        UUID orgId = booking.getListing().getOrgId();
        return orgId != null && organizationService.hasOrgPermission(
                userId, orgId, com.rentle.domain.platform.catalog.PermissionKeys.ORGANIZATION_BOOKING_MANAGE);
    }

    /** Incoming bookings for an organization (provider side), for its members' dashboard. */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> orgBookings(UUID userId, UUID orgId, Pageable pageable) {
        if (!organizationService.hasOrgPermission(userId, orgId,
                com.rentle.domain.platform.catalog.PermissionKeys.ORGANIZATION_BOOKING_MANAGE)) {
            throw new UnauthorizedException("You are not a member of this organization");
        }
        return toPageWithCovers(bookingRepository.findByProviderOrgId(orgId, pageable));
    }
}
