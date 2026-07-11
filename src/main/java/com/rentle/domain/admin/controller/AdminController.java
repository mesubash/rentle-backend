package com.rentle.domain.admin.controller;

import com.rentle.domain.admin.service.AdminService;
import com.rentle.domain.booking.dto.BookingResponse;
import com.rentle.domain.listing.dto.ListingSummaryResponse;
import com.rentle.domain.user.dto.UserProfileResponse;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.service.UserService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    public AdminController(AdminService adminService, UserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserProfileResponse>> users(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listUsers(status, pageable(page, size)));
    }

    @GetMapping("/users/{id}/citizenship")
    public ResponseEntity<Resource> citizenship(@PathVariable UUID id) {
        UserService.CitizenshipFile file = userService.loadCitizenship(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.resource());
    }

    @PutMapping("/users/{id}/verify")
    public ApiResponse<UserProfileResponse> verify(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.verifyCitizenship(id));
    }

    @PutMapping("/users/{id}/suspend")
    public ApiResponse<UserProfileResponse> suspend(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.suspend(id));
    }

    @PutMapping("/users/{id}/unsuspend")
    public ApiResponse<UserProfileResponse> unsuspend(@PathVariable UUID id) {
        return ApiResponse.ok(adminService.unsuspend(id));
    }

    @GetMapping("/bookings")
    public ApiResponse<PageResponse<BookingResponse>> bookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listBookings(pageable(page, size)));
    }

    @GetMapping("/listings")
    public ApiResponse<PageResponse<ListingSummaryResponse>> listings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listListings(pageable(page, size)));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
