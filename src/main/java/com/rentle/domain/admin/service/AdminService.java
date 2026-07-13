package com.rentle.domain.admin.service;

import com.rentle.domain.booking.dto.BookingResponse;
import com.rentle.domain.booking.repository.BookingRepository;
import com.rentle.domain.listing.dto.ListingSummaryResponse;
import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.ListingStatus;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.domain.user.dto.UserProfileResponse;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.security.TokenRevocationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Service
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final TokenRevocationService tokenRevocation;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository,
                        BookingRepository bookingRepository,
                        ListingRepository listingRepository,
                        TokenRevocationService tokenRevocation,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
        this.tokenRevocation = tokenRevocation;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Staff sets a new password for any account. Old sessions are revoked and any
     * failed-login lockout is cleared, so the user can sign in immediately with the
     * new password. The plaintext is never stored or logged.
     */
    @Transactional
    public void resetPassword(UUID actorId, UUID userId, String newPassword) {
        User user = getUser(userId);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        // Force re-authentication everywhere the password just changed under.
        tokenRevocation.revokeUser(userId);
        log.info("Password reset for user {} by {}", userId, actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listUsers(UserStatus status, Pageable pageable) {
        var page = status != null
                ? userRepository.findByStatus(status, pageable)
                : userRepository.findAll(pageable);
        return PageResponse.from(page, UserProfileResponse::from);
    }

    @Transactional
    public UserProfileResponse suspend(UUID actorId, UUID userId) {
        if (actorId.equals(userId)) {
            throw new RentleException("You cannot suspend your own account");
        }
        User user = getUser(userId);
        user.setStatus(UserStatus.SUSPENDED);
        user = userRepository.save(user);
        // Revoke any already-issued access tokens immediately (they otherwise
        // stay valid until their 15-minute expiry).
        tokenRevocation.revokeUser(userId);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse unsuspend(UUID userId) {
        User user = getUser(userId);
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new RentleException("User is not suspended");
        }
        user.setStatus(Boolean.TRUE.equals(user.getCitizenshipVerified())
                ? UserStatus.VERIFIED
                : UserStatus.PENDING_VERIFICATION);
        user = userRepository.save(user);
        tokenRevocation.clearUser(userId);
        return UserProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listBookings(Pageable pageable) {
        return PageResponse.from(bookingRepository.findAll(pageable), BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> listListings(Pageable pageable) {
        return PageResponse.from(listingRepository.findAll(pageable),
                l -> ListingSummaryResponse.from(l, null));
    }

    @Transactional
    public ListingSummaryResponse deactivateListing(UUID moderatorId, UUID listingId, String reason) {
        Listing listing = getListing(listingId);
        listing.setStatus(ListingStatus.INACTIVE);
        listing = listingRepository.save(listing);
        log.info("Listing {} deactivated by {}. Reason: {}", listingId, moderatorId, reason);
        return ListingSummaryResponse.from(listing, null);
    }

    @Transactional
    public ListingSummaryResponse removeListing(UUID moderatorId, UUID listingId, String reason) {
        Listing listing = getListing(listingId);
        if (listing.getStatus() == ListingStatus.REMOVED) {
            throw new RentleException("Listing is already removed");
        }
        listing.setStatus(ListingStatus.REMOVED);
        listing = listingRepository.save(listing);
        log.info("Listing {} removed by {}. Reason: {}", listingId, moderatorId, reason);
        return ListingSummaryResponse.from(listing, null);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Listing getListing(UUID listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
    }
}
