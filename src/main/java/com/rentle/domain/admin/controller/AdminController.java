package com.rentle.domain.admin.controller;

import com.rentle.domain.admin.service.AdminService;
import com.rentle.domain.booking.dto.BookingResponse;
import com.rentle.domain.listing.dto.AdminCategoryRow;
import com.rentle.domain.listing.dto.CategoryStatusRequest;
import com.rentle.domain.listing.dto.ListingSummaryResponse;
import com.rentle.domain.listing.service.CategoryService;
import com.rentle.domain.platform.settings.SettingValueRequest;
import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.user.dto.KycAdminRow;
import com.rentle.domain.user.dto.KycResponse;
import com.rentle.domain.user.dto.ReasonRequest;
import com.rentle.domain.user.dto.ResetPasswordRequest;
import com.rentle.domain.user.dto.UserProfileResponse;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.service.KycService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;
    private final KycService kycService;
    private final CategoryService categoryService;
    private final com.rentle.domain.platform.settings.PlatformSettingsService platformSettings;
    private final com.rentle.domain.booking.service.BookingService bookingService;

    public AdminController(AdminService adminService, KycService kycService, CategoryService categoryService,
                          com.rentle.domain.platform.settings.PlatformSettingsService platformSettings,
                          com.rentle.domain.booking.service.BookingService bookingService) {
        this.adminService = adminService;
        this.kycService = kycService;
        this.categoryService = categoryService;
        this.platformSettings = platformSettings;
        this.bookingService = bookingService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('" + PermissionKeys.IDENTITY_USER_READ + "')")
    public ApiResponse<PageResponse<UserProfileResponse>> users(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listUsers(status, pageable(page, size)));
    }

    // --- KYC review queue ---

    @GetMapping("/kyc")
    @PreAuthorize("hasAuthority('" + PermissionKeys.KYC_SUBMISSION_READ + "')")
    public ApiResponse<PageResponse<KycAdminRow>> kycQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.from(
                kycService.pending(pageable(page, size)), KycAdminRow::from));
    }

    @GetMapping("/kyc/{userId}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.KYC_SUBMISSION_READ + "')")
    public ApiResponse<KycResponse> kycDetail(@PathVariable UUID userId) {
        return ApiResponse.ok(kycService.detail(userId));
    }

    @GetMapping("/users/{id}/citizenship")
    @PreAuthorize("hasAuthority('" + PermissionKeys.KYC_SUBMISSION_READ + "')")
    public ResponseEntity<Resource> citizenship(@PathVariable UUID id,
                                                @RequestParam(defaultValue = "front") String side) {
        KycService.KycImage image = kycService.loadDocument(id, side);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(image.resource());
    }

    @PutMapping("/users/{id}/verify")
    @PreAuthorize("hasAuthority('" + PermissionKeys.KYC_SUBMISSION_APPROVE + "')")
    public ApiResponse<KycResponse> approveKyc(@PathVariable UUID id) {
        return ApiResponse.ok(kycService.approve(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/users/{id}/reject-kyc")
    @PreAuthorize("hasAuthority('" + PermissionKeys.KYC_SUBMISSION_REJECT + "')")
    public ApiResponse<KycResponse> rejectKyc(@PathVariable UUID id,
                                              @Valid @RequestBody(required = false) ReasonRequest request) {
        return ApiResponse.ok(kycService.reject(
                SecurityUtils.currentUserId(), id, request != null ? request.reason() : null));
    }

    @PutMapping("/users/{id}/suspend")
    @PreAuthorize("hasAuthority('" + PermissionKeys.IDENTITY_USER_SUSPEND + "')")
    public ApiResponse<UserProfileResponse> suspend(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.suspend(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/users/{id}/unsuspend")
    @PreAuthorize("hasAuthority('" + PermissionKeys.IDENTITY_USER_SUSPEND + "')")
    public ApiResponse<UserProfileResponse> unsuspend(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.unsuspend(id));
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasAuthority('" + PermissionKeys.IDENTITY_USER_RESET_PASSWORD + "')")
    public ApiResponse<String> resetPassword(@PathVariable UUID id,
                                             @Valid @RequestBody ResetPasswordRequest request) {
        adminService.resetPassword(SecurityUtils.currentUserId(), id, request.password());
        return ApiResponse.ok("Password updated");
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasAuthority('" + PermissionKeys.BOOKING_BOOKING_READ + "')")
    public ApiResponse<PageResponse<BookingResponse>> bookings(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listBookings(q, status, type, pageable(page, size)));
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.BOOKING_BOOKING_READ + "')")
    public ApiResponse<BookingResponse> booking(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.getBooking(id));
    }

    @GetMapping("/listings")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LISTING_LISTING_READ + "')")
    public ApiResponse<PageResponse<ListingSummaryResponse>> listings(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listListings(q, status, type, pageable(page, size)));
    }

    @PutMapping("/listings/{id}/deactivate")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LISTING_LISTING_MODERATE + "')")
    public ApiResponse<ListingSummaryResponse> deactivateListing(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReasonRequest request) {
        return ApiResponse.ok(adminService.deactivateListing(
                SecurityUtils.currentUserId(), id, request != null ? request.reason() : null));
    }

    @PutMapping("/listings/{id}/remove")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LISTING_LISTING_MODERATE + "')")
    public ApiResponse<ListingSummaryResponse> removeListing(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ReasonRequest request) {
        return ApiResponse.ok(adminService.removeListing(
                SecurityUtils.currentUserId(), id, request != null ? request.reason() : null));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LISTING_CATEGORY_MANAGE + "')")
    public ApiResponse<java.util.List<AdminCategoryRow>> categories() {
        return ApiResponse.ok(categoryService.listForAdmin());
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_SETTINGS_MANAGE + "')")
    public ApiResponse<java.util.Map<String, String>> settings() {
        return ApiResponse.ok(platformSettings.all());
    }

    @PutMapping("/settings/{key}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_SETTINGS_MANAGE + "')")
    public ApiResponse<java.util.Map<String, String>> updateSetting(
            @PathVariable String key, @Valid @RequestBody SettingValueRequest request) {
        platformSettings.set(key, request.value(), SecurityUtils.currentUserId());
        return ApiResponse.ok(platformSettings.all());
    }

    @GetMapping("/fees")
    @PreAuthorize("hasAuthority('" + PermissionKeys.BOOKING_FEE_MANAGE + "')")
    public ApiResponse<PageResponse<BookingResponse>> fees(
            @RequestParam(defaultValue = "false") boolean invoiced,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(bookingService.listFees(invoiced, pageable(page, size)));
    }

    @PutMapping("/bookings/{id}/fee-invoiced")
    @PreAuthorize("hasAuthority('" + PermissionKeys.BOOKING_FEE_MANAGE + "')")
    public ApiResponse<BookingResponse> markFeeInvoiced(@PathVariable UUID id) {
        return ApiResponse.ok(bookingService.markFeeInvoiced(id));
    }

    @PutMapping("/categories/{id}/status")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LISTING_CATEGORY_MANAGE + "')")
    public ApiResponse<AdminCategoryRow> setCategoryStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryStatusRequest request) {
        return ApiResponse.ok(categoryService.setActive(id, request.active()));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
