package com.rentle.domain.admin.service;

import com.rentle.domain.booking.dto.BookingResponse;
import com.rentle.domain.booking.repository.BookingRepository;
import com.rentle.domain.listing.dto.ListingSummaryResponse;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.domain.user.dto.UserProfileResponse;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.notification.SmsService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final SmsService smsService;

    public AdminService(UserRepository userRepository,
                        BookingRepository bookingRepository,
                        ListingRepository listingRepository,
                        SmsService smsService) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
        this.smsService = smsService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listUsers(UserStatus status, Pageable pageable) {
        var page = status != null
                ? userRepository.findByStatus(status, pageable)
                : userRepository.findAll(pageable);
        return PageResponse.from(page, UserProfileResponse::from);
    }

    @Transactional
    public UserProfileResponse verifyCitizenship(UUID userId) {
        User user = getUser(userId);
        if (user.getCitizenshipCardUrl() == null) {
            throw new RentleException("User has not uploaded a citizenship card");
        }
        if (!Boolean.TRUE.equals(user.getPhoneVerified())) {
            throw new RentleException("Phone must be verified before citizenship approval");
        }
        user.setCitizenshipVerified(true);
        user.setStatus(UserStatus.VERIFIED);
        user = userRepository.save(user);
        smsService.send(user.getPhoneNumber(),
                "Your Rentle identity verification is approved. You can now create listings.");
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse suspend(UUID userId) {
        User user = getUser(userId);
        user.setStatus(UserStatus.SUSPENDED);
        return UserProfileResponse.from(userRepository.save(user));
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
        return UserProfileResponse.from(userRepository.save(user));
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

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
