package com.rentle.domain.booking.controller;

import com.rentle.domain.booking.dto.AdjustPriceRequest;
import com.rentle.domain.booking.dto.BookingActionRequest;
import com.rentle.domain.booking.dto.BookingResponse;
import com.rentle.domain.booking.dto.CreateBookingRequest;
import com.rentle.domain.booking.service.BookingService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request) {
        return ApiResponse.ok(bookingService.createBooking(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/me/as-renter")
    public ApiResponse<PageResponse<BookingResponse>> asRenter(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ApiResponse.ok(bookingService.myBookingsAsRenter(SecurityUtils.currentUserId(), pageable));
    }

    @GetMapping("/me/as-owner")
    public ApiResponse<PageResponse<BookingResponse>> asOwner(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        UUID userId = SecurityUtils.currentUserId();
        return ApiResponse.ok(orgId != null
                ? bookingService.orgBookings(userId, orgId, pageable)
                : bookingService.myBookingsAsOwner(userId, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingResponse> detail(@PathVariable UUID id) {
        return ApiResponse.ok(bookingService.getDetail(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<BookingResponse> approve(@PathVariable UUID id) {
        return ApiResponse.ok(bookingService.approve(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/assign-worker")
    public ApiResponse<BookingResponse> assignWorker(@PathVariable UUID id,
                                                     @RequestParam(value = "workerId", required = false) UUID workerId) {
        return ApiResponse.ok(bookingService.assignWorker(SecurityUtils.currentUserId(), id, workerId));
    }

    @PostMapping("/{id}/price")
    public ApiResponse<BookingResponse> adjustPrice(@PathVariable UUID id,
                                                    @Valid @RequestBody AdjustPriceRequest request) {
        return ApiResponse.ok(bookingService.adjustPrice(
                SecurityUtils.currentUserId(), id, request.totalPrice()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<BookingResponse> reject(@PathVariable UUID id,
                                               @Valid @RequestBody(required = false) BookingActionRequest request) {
        return ApiResponse.ok(bookingService.reject(
                SecurityUtils.currentUserId(), id, request != null ? request.reason() : null));
    }

    @PostMapping("/{id}/deposit")
    public ApiResponse<BookingResponse> uploadDeposit(@PathVariable UUID id,
                                                      @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(bookingService.uploadDepositProof(SecurityUtils.currentUserId(), id, file));
    }

    @PostMapping("/{id}/confirm-deposit")
    public ApiResponse<BookingResponse> confirmDeposit(@PathVariable UUID id) {
        return ApiResponse.ok(bookingService.confirmDeposit(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/{id}/deposit-proof")
    public ResponseEntity<Resource> depositProof(@PathVariable UUID id) {
        BookingService.DepositProof proof = bookingService.loadDepositProof(SecurityUtils.currentUserId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proof.contentType()))
                .body(proof.resource());
    }

    @PostMapping("/{id}/condition")
    public ApiResponse<BookingResponse> recordCondition(@PathVariable UUID id,
                                                        @RequestParam("phase") String phase,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam(value = "note", required = false) String note) {
        return ApiResponse.ok(bookingService.recordCondition(SecurityUtils.currentUserId(), id, phase, file, note));
    }

    @GetMapping("/{id}/condition/{phase}")
    public ResponseEntity<Resource> condition(@PathVariable UUID id, @PathVariable String phase) {
        BookingService.DepositProof img = bookingService.loadCondition(SecurityUtils.currentUserId(), id, phase);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .body(img.resource());
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<BookingResponse> complete(@PathVariable UUID id) {
        return ApiResponse.ok(bookingService.complete(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingResponse> cancel(@PathVariable UUID id,
                                               @Valid @RequestBody(required = false) BookingActionRequest request) {
        return ApiResponse.ok(bookingService.cancel(
                SecurityUtils.currentUserId(), id, request != null ? request.reason() : null));
    }
}
