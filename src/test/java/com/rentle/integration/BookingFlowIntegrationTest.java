package com.rentle.integration;

import com.rentle.config.TestcontainersConfig;
import com.rentle.domain.booking.dto.BookingResponse;
import com.rentle.domain.booking.dto.CreateBookingRequest;
import com.rentle.domain.booking.model.Booking;
import com.rentle.domain.booking.model.BookingStatus;
import com.rentle.domain.booking.repository.BookingRepository;
import com.rentle.domain.booking.service.BookingService;
import com.rentle.domain.listing.dto.BlockDatesRequest;
import com.rentle.domain.listing.dto.CreateListingRequest;
import com.rentle.domain.listing.dto.ProductDetailDto;
import com.rentle.domain.listing.model.Category;
import com.rentle.domain.listing.model.ItemCondition;
import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.ListingStatus;
import com.rentle.domain.listing.model.ListingType;
import com.rentle.domain.listing.model.PriceUnit;
import com.rentle.domain.listing.repository.CategoryRepository;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.domain.listing.service.AvailabilityService;
import com.rentle.domain.listing.service.ListingService;
import com.rentle.domain.messaging.dto.SendMessageRequest;
import com.rentle.domain.messaging.service.MessageService;
import com.rentle.domain.review.dto.CreateReviewRequest;
import com.rentle.domain.review.service.ReviewService;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.BookingConflictException;
import com.rentle.shared.exception.InvalidStateTransitionException;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfig.class)
class BookingFlowIntegrationTest {

    @Autowired BookingService bookingService;
    @Autowired ReviewService reviewService;
    @Autowired MessageService messageService;
    @Autowired ListingService listingService;
    @Autowired AvailabilityService availabilityService;
    @Autowired BookingRepository bookingRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;

    private User owner;
    private User renter;

    @BeforeEach
    void setUp() {
        owner = createUser(UserStatus.VERIFIED);
        renter = createUser(UserStatus.VERIFIED);
    }

    private User createUser(UserStatus status) {
        long n = System.nanoTime() % 1_000_000_000L;
        User u = new User();
        u.setPhoneNumber("+97797" + n);
        u.setEmail("u" + n + "@test.com");
        u.setPasswordHash("$2a$12$test");
        u.setFullName("Test User " + n);
        u.setStatus(status);
        u.setPhoneVerified(true);
        u.setEmailVerified(true);
        u.setCitizenshipVerified(status == UserStatus.VERIFIED);
        return userRepository.save(u);
    }

    private Listing createActiveListing(User listingOwner, String deposit) {
        Category category = categoryRepository.findBySlug("cameras-tech").orElseThrow();
        Listing l = new Listing();
        l.setOwner(listingOwner);
        l.setCategory(category);
        l.setType(ListingType.PRODUCT);
        l.setStatus(ListingStatus.ACTIVE);
        l.setTitle("Canon EOS R5 test kit");
        l.setDescription("Full-frame mirrorless camera with two lenses for rent.");
        l.setPricePerUnit(new BigDecimal("1000.00"));
        l.setPriceUnit(PriceUnit.PER_DAY);
        l.setDistrict("Kathmandu");
        l.setDepositAmount(new BigDecimal(deposit));
        return listingRepository.save(l);
    }

    private CreateBookingRequest bookingRequest(UUID listingId, int startOffsetDays, int endOffsetDays) {
        return new CreateBookingRequest(listingId,
                LocalDate.now().plusDays(startOffsetDays),
                LocalDate.now().plusDays(endOffsetDays),
                null, null, "test booking");
    }

    @Test
    void fullLifecycleWithDepositProofAndReviews() {
        Listing listing = createActiveListing(owner, "5000.00");

        BookingResponse booking = bookingService.createBooking(
                renter.getId(), bookingRequest(listing.getId(), 5, 7));
        assertEquals("REQUESTED", booking.status());
        assertEquals(new BigDecimal("3000.00"), booking.totalPrice());

        booking = bookingService.approve(owner.getId(), booking.id());
        assertEquals("APPROVED", booking.status());

        MockMultipartFile proof = new MockMultipartFile(
                "file", "proof.png", "image/png", new byte[]{1, 2, 3});
        booking = bookingService.uploadDepositProof(renter.getId(), booking.id(), proof);
        assertEquals("DEPOSIT_PENDING", booking.status());
        assertNotNull(booking.depositProofUrl());

        booking = bookingService.confirmDeposit(owner.getId(), booking.id());
        assertEquals("ACTIVE", booking.status());
        assertTrue(booking.depositPaid());

        // Messaging works for participants once approved
        messageService.send(renter.getId(), booking.id(), new SendMessageRequest("When can I pick up?"));

        booking = bookingService.complete(owner.getId(), booking.id());
        assertEquals("COMPLETED", booking.status());

        // Dual-sided reviews
        reviewService.create(renter.getId(), new CreateReviewRequest(booking.id(), 5, "Great owner"));
        reviewService.create(owner.getId(), new CreateReviewRequest(booking.id(), 4, "Careful renter"));

        // DB triggers recalculated aggregates. The listing rating reflects only
        // reviews *about the owner* (the renter's 5-star), not the owner's review
        // of the renter — so count is 1 and average is 5.00, not 2 reviews / 4.5.
        Listing refreshed = listingRepository.findById(listing.getId()).orElseThrow();
        assertEquals(1, refreshed.getReviewCount());
        assertEquals(0, new BigDecimal("5.00").compareTo(refreshed.getAverageRating()));
        assertEquals(1, refreshed.getTotalBookings());

        User refreshedOwner = userRepository.findById(owner.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5.00").compareTo(refreshedOwner.getTrustScore()));

        // Duplicate review blocked
        UUID bookingId = booking.id();
        assertThrows(RentleException.class, () ->
                reviewService.create(renter.getId(), new CreateReviewRequest(bookingId, 3, "again")));
    }

    @Test
    void zeroDepositBookingSkipsProofUpload() {
        Listing listing = createActiveListing(owner, "0.00");
        BookingResponse booking = bookingService.createBooking(
                renter.getId(), bookingRequest(listing.getId(), 5, 6));
        bookingService.approve(owner.getId(), booking.id());
        BookingResponse active = bookingService.confirmDeposit(owner.getId(), booking.id());
        assertEquals("ACTIVE", active.status());
    }

    @Test
    void preventsDoubleBookingAtServiceLevel() {
        Listing listing = createActiveListing(owner, "0.00");
        bookingService.createBooking(renter.getId(), bookingRequest(listing.getId(), 5, 8));

        User secondRenter = createUser(UserStatus.VERIFIED);
        assertThrows(BookingConflictException.class, () ->
                bookingService.createBooking(secondRenter.getId(), bookingRequest(listing.getId(), 7, 9)));
    }

    @Test
    void preventsDoubleBookingAtDatabaseLevel() {
        Listing listing = createActiveListing(owner, "0.00");
        bookingService.createBooking(renter.getId(), bookingRequest(listing.getId(), 5, 8));

        // Bypass the service pre-check — the GiST exclusion constraint must still reject
        User secondRenter = createUser(UserStatus.VERIFIED);
        Booking overlap = new Booking();
        overlap.setListing(listingRepository.findById(listing.getId()).orElseThrow());
        overlap.setRenter(secondRenter);
        overlap.setStartDate(LocalDate.now().plusDays(6));
        overlap.setEndDate(LocalDate.now().plusDays(10));
        overlap.setTotalPrice(new BigDecimal("5000.00"));
        assertThrows(Exception.class, () -> bookingRepository.saveAndFlush(overlap));
    }

    @Test
    void preventsSelfBooking() {
        Listing listing = createActiveListing(owner, "0.00");
        assertThrows(RentleException.class, () ->
                bookingService.createBooking(owner.getId(), bookingRequest(listing.getId(), 5, 6)));
    }

    @Test
    void ownerBlockedDatesPreventBooking() {
        Listing listing = createActiveListing(owner, "0.00");
        availabilityService.blockDates(owner.getId(), listing.getId(),
                new BlockDatesRequest(LocalDate.now().plusDays(5), LocalDate.now().plusDays(10), "offline booking"));

        assertThrows(BookingConflictException.class, () ->
                bookingService.createBooking(renter.getId(), bookingRequest(listing.getId(), 7, 8)));
    }

    @Test
    void rejectsInvalidStateTransition() {
        Listing listing = createActiveListing(owner, "0.00");
        BookingResponse booking = bookingService.createBooking(
                renter.getId(), bookingRequest(listing.getId(), 5, 6));

        // REQUESTED -> COMPLETED is illegal
        assertThrows(InvalidStateTransitionException.class, () ->
                bookingService.complete(owner.getId(), booking.id()));
    }

    @Test
    void reviewOnlyAllowedAfterCompletion() {
        Listing listing = createActiveListing(owner, "0.00");
        BookingResponse booking = bookingService.createBooking(
                renter.getId(), bookingRequest(listing.getId(), 5, 6));

        assertThrows(RentleException.class, () ->
                reviewService.create(renter.getId(), new CreateReviewRequest(booking.id(), 5, "too early")));
    }

    @Test
    void nonParticipantCannotReadMessages() {
        Listing listing = createActiveListing(owner, "0.00");
        BookingResponse booking = bookingService.createBooking(
                renter.getId(), bookingRequest(listing.getId(), 5, 6));

        User outsider = createUser(UserStatus.VERIFIED);
        assertThrows(UnauthorizedException.class, () ->
                messageService.getMessages(outsider.getId(), booking.id(), PageRequest.of(0, 10)));
    }

    @Test
    void unverifiedUserCannotCreateListing() {
        User pending = createUser(UserStatus.PENDING_VERIFICATION);
        Category category = categoryRepository.findBySlug("cameras-tech").orElseThrow();
        CreateListingRequest req = new CreateListingRequest(
                "Nikon D850 body", "Well maintained DSLR body available for rent in Kathmandu.",
                category.getId(), ListingType.PRODUCT, new BigDecimal("800.00"), PriceUnit.PER_DAY,
                "Kathmandu", null, BigDecimal.ZERO,
                new ProductDetailDto(ItemCondition.GOOD, "Nikon", "D850", 1, 30), null);

        assertThrows(UnauthorizedException.class, () -> listingService.create(pending.getId(), req));
    }

    // --- Hourly service bookings: time-aware overlap ---

    private Listing createHourlyService(User listingOwner) {
        Category category = categoryRepository.findBySlug("event-photography").orElseThrow();
        Listing l = new Listing();
        l.setOwner(listingOwner);
        l.setCategory(category);
        l.setType(ListingType.SERVICE);
        l.setStatus(ListingStatus.ACTIVE);
        l.setTitle("Event photographer for hire");
        l.setDescription("Professional event photography charged by the hour in Kathmandu valley.");
        l.setPricePerUnit(new BigDecimal("500.00"));
        l.setPriceUnit(PriceUnit.PER_HOUR);
        l.setDistrict("Kathmandu");
        l.setDepositAmount(BigDecimal.ZERO);
        return listingRepository.save(l);
    }

    private CreateBookingRequest hourlyRequest(UUID listingId, int dayOffset, LocalTime start, LocalTime end) {
        LocalDate day = LocalDate.now().plusDays(dayOffset);
        return new CreateBookingRequest(listingId, day, day, start, end, "test");
    }

    @Test
    void hourlyServiceAllowsNonOverlappingSameDaySlots() {
        Listing listing = createHourlyService(owner);
        bookingService.createBooking(renter.getId(),
                hourlyRequest(listing.getId(), 5, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        User secondRenter = createUser(UserStatus.VERIFIED);
        BookingResponse second = bookingService.createBooking(secondRenter.getId(),
                hourlyRequest(listing.getId(), 5, LocalTime.of(14, 0), LocalTime.of(16, 0)));
        assertEquals("REQUESTED", second.status());
        assertEquals(new BigDecimal("1000.00"), second.totalPrice()); // 500/hr × 2h
    }

    @Test
    void hourlyServiceRejectsOverlappingSameDaySlots() {
        Listing listing = createHourlyService(owner);
        bookingService.createBooking(renter.getId(),
                hourlyRequest(listing.getId(), 5, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        User secondRenter = createUser(UserStatus.VERIFIED);
        assertThrows(Exception.class, () -> bookingService.createBooking(secondRenter.getId(),
                hourlyRequest(listing.getId(), 5, LocalTime.of(11, 0), LocalTime.of(13, 0))));
    }

    @Test
    void unverifiedUserCannotBook() {
        Listing listing = createActiveListing(owner, "0.00");
        User pending = createUser(UserStatus.PENDING_VERIFICATION);
        assertThrows(UnauthorizedException.class, () ->
                bookingService.createBooking(pending.getId(), bookingRequest(listing.getId(), 5, 6)));
    }

    @Test
    void hourlyBookingRejectsMultiDaySpan() {
        Listing listing = createHourlyService(owner);
        LocalDate day = LocalDate.now().plusDays(5);
        CreateBookingRequest multiDay = new CreateBookingRequest(
                listing.getId(), day, day.plusDays(1), LocalTime.of(9, 0), LocalTime.of(11, 0), null);
        assertThrows(RentleException.class,
                () -> bookingService.createBooking(renter.getId(), multiDay));
    }
}
