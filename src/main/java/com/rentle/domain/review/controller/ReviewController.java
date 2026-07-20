package com.rentle.domain.review.controller;

import com.rentle.domain.review.dto.CreateReviewRequest;
import com.rentle.domain.review.dto.ReviewResponse;
import com.rentle.domain.review.service.ReviewService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(@Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.ok(reviewService.create(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/bookings/{id}/my-review-status")
    public ApiResponse<Boolean> myReviewStatus(@PathVariable UUID id) {
        return ApiResponse.ok(reviewService.hasReviewed(id, SecurityUtils.currentUserId()));
    }

    @GetMapping("/listings/{id}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> forListing(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ApiResponse.ok(reviewService.forListing(id, pageable));
    }

    @GetMapping("/users/{id}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> aboutUser(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ApiResponse.ok(reviewService.aboutUser(id, pageable));
    }
}
