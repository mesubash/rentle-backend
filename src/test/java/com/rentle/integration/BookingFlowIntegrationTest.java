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
    @Autowired com.rentle.domain.admin.service.AdminService adminService;
    @Autowired ReviewService reviewService;
    @Autowired MessageService messageService;
    @Autowired ListingService listingService;
    @Autowired com.rentle.domain.listing.service.ListingImageService listingImageService;
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
                null, null, "test booking", null);
    }

    @Test
    void fullLifecycleWithDepositProofAndReviews() {
        Listing listing = createActiveListing(owner, "5000.00");

        // Start today so the booking can be legitimately completed later in the flow
        // (completion requires the rental period to have started). 0..2 = 3 days = 3000.
        BookingResponse booking = bookingService.createBooking(
                renter.getId(), bookingRequest(listing.getId(), 0, 2));
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
    void overlappingRequestsAreAllowedUntilOneIsApproved() {
        // P1-27: a pending request does not hold dates, so two renters may both request the
        // same window. Exclusivity is claimed at approval.
        Listing listing = createActiveListing(owner, "0.00");
        bookingService.createBooking(renter.getId(), bookingRequest(listing.getId(), 5, 8));

        User secondRenter = createUser(UserStatus.VERIFIED);
        BookingResponse second = bookingService.createBooking(
                secondRenter.getId(), bookingRequest(listing.getId(), 7, 9));
        assertEquals("REQUESTED", second.status());
    }

    @Test
    void preventsDoubleBookingAtServiceLevel() {
        // Once a booking is APPROVED it holds the dates: a later overlapping *request* is rejected.
        Listing listing = createActiveListing(owner, "0.00");
        BookingResponse first = bookingService.createBooking(renter.getId(), bookingRequest(listing.getId(), 5, 8));
        bookingService.approve(owner.getId(), first.id());

        User secondRenter = createUser(UserStatus.VERIFIED);
        assertThrows(BookingConflictException.class, () ->
                bookingService.createBooking(secondRenter.getId(), bookingRequest(listing.getId(), 7, 9)));
    }

    @Test
    void preventsDoubleBookingAtDatabaseLevel() {
        Listing listing = createActiveListing(owner, "0.00");
        BookingResponse first = bookingService.createBooking(renter.getId(), bookingRequest(listing.getId(), 5, 8));
        bookingService.approve(owner.getId(), first.id());

        // Bypass the service pre-check — the GiST exclusion constraint must still reject a
        // second APPROVED booking over the same dates (REQUESTED no longer participates).
        User secondRenter = createUser(UserStatus.VERIFIED);
        Booking overlap = new Booking();
        overlap.setListing(listingRepository.findById(listing.getId()).orElseThrow());
        overlap.setRenter(secondRenter);
        overlap.setStartDate(LocalDate.now().plusDays(6));
        overlap.setEndDate(LocalDate.now().plusDays(10));
        overlap.setStatus(com.rentle.domain.booking.model.BookingStatus.APPROVED);
        overlap.setTotalPrice(new BigDecimal("5000.00"));
        assertThrows(Exception.class, () -> bookingRepository.saveAndFlush(overlap));
    }

    @Test
    void adminBookingListMapsWithoutLazyInitError() {
        // Regression: listBookings maps BookingResponse (which reads listing/owner) and must do
        // so inside a session — otherwise a populated list throws LazyInitializationException.
        Listing listing = createActiveListing(owner, "0.00");
        bookingService.createBooking(renter.getId(), bookingRequest(listing.getId(), 0, 2));
        var page = adminService.listBookings(null, null, null, PageRequest.of(0, 20));
        assertTrue(page.content().stream().anyMatch(b -> b.listingTitle() != null));
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
                "Kathmandu", null, BigDecimal.ZERO, null, null, null,
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
        return new CreateBookingRequest(listingId, day, day, start, end, "test", null);
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
        // Time-aware GiST constraint still prevents two APPROVED hourly bookings from
        // overlapping (P1-27 only frees REQUESTED). Approve the first, then a direct
        // APPROVED save of an overlapping window must be rejected by the DB.
        Listing listing = createHourlyService(owner);
        BookingResponse first = bookingService.createBooking(renter.getId(),
                hourlyRequest(listing.getId(), 5, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        bookingService.approve(owner.getId(), first.id());

        User secondRenter = createUser(UserStatus.VERIFIED);
        Booking overlap = new Booking();
        overlap.setListing(listingRepository.findById(listing.getId()).orElseThrow());
        overlap.setRenter(secondRenter);
        LocalDate day = LocalDate.now().plusDays(5);
        overlap.setStartDate(day);
        overlap.setEndDate(day);
        overlap.setStartTime(LocalTime.of(11, 0));
        overlap.setEndTime(LocalTime.of(13, 0));
        overlap.setStatus(com.rentle.domain.booking.model.BookingStatus.APPROVED);
        overlap.setTotalPrice(new BigDecimal("1000.00"));
        assertThrows(Exception.class, () -> bookingRepository.saveAndFlush(overlap));
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
                listing.getId(), day, day.plusDays(1), LocalTime.of(9, 0), LocalTime.of(11, 0), null, null);
        assertThrows(RentleException.class,
                () -> bookingService.createBooking(renter.getId(), multiDay));
    }

    @Test
    void threadSummaryCarriesBookingContextForBothParticipants() {
        Listing listing = createActiveListing(owner, "0.00");
        BookingResponse booking = bookingService.createBooking(
                renter.getId(), bookingRequest(listing.getId(), 1, 3));
        messageService.send(renter.getId(), booking.id(), new SendMessageRequest("Is this available?"));

        // The inbox reads these fields instead of fetching every booking just to look up a
        // title and a name, so the query must resolve them per viewer.
        var renterView = messageService.threadSummaries(renter.getId());
        assertEquals(1, renterView.size());
        var summary = renterView.get(0);
        assertEquals(booking.id(), summary.bookingId());
        assertEquals(listing.getTitle(), summary.listingTitle());
        assertEquals(owner.getId(), summary.ownerId());
        assertEquals(owner.getFullName(), summary.ownerName());
        assertEquals(renter.getFullName(), summary.renterName());
        assertEquals(BookingStatus.REQUESTED, summary.status());
        assertNotNull(summary.lastMessageAt());
        assertEquals(0L, summary.unreadCount());

        // The owner has not read the renter's message yet.
        var ownerView = messageService.threadSummaries(owner.getId());
        assertEquals(1, ownerView.size());
        assertEquals(1L, ownerView.get(0).unreadCount());
        assertEquals(listing.getTitle(), ownerView.get(0).listingTitle());
    }

    @Test
    void bookingCarriesListingCoverImage() throws Exception {
        Listing listing = createActiveListing(owner, "0.00");
        listingImageService.addImages(owner.getId(), listing.getId(), java.util.List.of(
                new MockMultipartFile("files", "cover.jpg", "image/jpeg", new byte[]{1, 2, 3})));

        BookingResponse booking = bookingService.createBooking(
                renter.getId(), bookingRequest(listing.getId(), 1, 3));

        // Detail and list paths both resolve the cover, so the client no longer has to fetch
        // the whole listing to render a thumbnail.
        assertNotNull(bookingService.getDetail(renter.getId(), booking.id()).coverImage());
        var page = bookingService.myBookingsAsRenter(renter.getId(), PageRequest.of(0, 10));
        assertNotNull(page.content().get(0).coverImage());
    }

    @Test
    void adminSearchFiltersServerSide() {
        Listing listing = createActiveListing(owner, "0.00");
        bookingService.createBooking(renter.getId(), bookingRequest(listing.getId(), 1, 3));

        // Null filters must be treated as "no filter" - a null enum parameter in the
        // IS NULL guard is the part most likely to fail at runtime rather than compile.
        var unfiltered = adminService.listBookings(null, null, null, PageRequest.of(0, 20));
        assertTrue(unfiltered.totalElements() >= 1);

        var byStatus = adminService.listBookings(null, "REQUESTED", null, PageRequest.of(0, 20));
        assertTrue(byStatus.content().stream().allMatch(b -> "REQUESTED".equals(b.status())));

        var byType = adminService.listBookings(null, null, "PRODUCT", PageRequest.of(0, 20));
        assertTrue(byType.totalElements() >= 1);

        var byQuery = adminService.listBookings("Canon EOS", null, null, PageRequest.of(0, 20));
        assertTrue(byQuery.content().stream().anyMatch(b -> b.listingTitle().contains("Canon")));

        var noMatch = adminService.listBookings("zzz-no-such-listing", null, null, PageRequest.of(0, 20));
        assertEquals(0, noMatch.totalElements());

        // An unknown enum value must not 500; it is ignored like a blank filter.
        var bogus = adminService.listBookings(null, "NOT_A_STATUS", null, PageRequest.of(0, 20));
        assertTrue(bogus.totalElements() >= 1);

        var listings = adminService.listListings("Canon", "ACTIVE", "PRODUCT", PageRequest.of(0, 20));
        assertTrue(listings.totalElements() >= 1);
    }
}
