package com.rentle.domain.booking.controller;

import com.rentle.domain.booking.dto.BookingActionRequest;
import com.rentle.domain.booking.dto.BookingResponse;
import com.rentle.domain.booking.dto.CreateBookingRequest;
import com.rentle.domain.booking.service.BookingService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ApiResponse.ok(bookingService.myBookingsAsOwner(SecurityUtils.currentUserId(), pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingResponse> detail(@PathVariable UUID id) {
        return ApiResponse.ok(bookingService.getDetail(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<BookingResponse> approve(@PathVariable UUID id) {
        return ApiResponse.ok(bookingService.approve(SecurityUtils.currentUserId(), id));
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
