package com.rentle.domain.listing.controller;

import com.rentle.domain.listing.dto.AvailabilityResponse;
import com.rentle.domain.listing.dto.BlockDatesRequest;
import com.rentle.domain.listing.dto.CreateListingRequest;
import com.rentle.domain.listing.dto.ListingResponse;
import com.rentle.domain.listing.dto.ListingSummaryResponse;
import com.rentle.domain.listing.dto.UpdateListingRequest;
import com.rentle.domain.listing.model.ListingType;
import com.rentle.domain.listing.service.AvailabilityService;
import com.rentle.domain.listing.service.ListingImageService;
import com.rentle.domain.listing.service.ListingSearchService;
import com.rentle.domain.listing.service.ListingService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ListingController {

    private final ListingService listingService;
    private final ListingSearchService listingSearchService;
    private final ListingImageService listingImageService;
    private final AvailabilityService availabilityService;

    public ListingController(ListingService listingService,
                             ListingSearchService listingSearchService,
                             ListingImageService listingImageService,
                             AvailabilityService availabilityService) {
        this.listingService = listingService;
        this.listingSearchService = listingSearchService;
        this.listingImageService = listingImageService;
        this.availabilityService = availabilityService;
    }

    @PostMapping("/listings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ListingResponse> create(@Valid @RequestBody CreateListingRequest request) {
        return ApiResponse.ok(listingService.create(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/listings")
    public ApiResponse<PageResponse<ListingSummaryResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ListingType type,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ApiResponse.ok(listingSearchService.search(q, type, categoryId, district, minPrice, maxPrice, sort, pageable));
    }

    @GetMapping("/listings/me")
    public ApiResponse<PageResponse<ListingSummaryResponse>> myListings(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID userId = SecurityUtils.currentUserId();
        return ApiResponse.ok(orgId != null
                ? listingService.orgListings(userId, orgId, pageable)
                : listingService.myListings(userId, pageable));
    }

    @GetMapping("/listings/{id}")
    public ApiResponse<ListingResponse> detail(@PathVariable UUID id) {
        return ApiResponse.ok(listingService.getDetail(id, SecurityUtils.currentUserIdOrNull()));
    }

    @PutMapping("/listings/{id}")
    public ApiResponse<ListingResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateListingRequest request) {
        return ApiResponse.ok(listingService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/listings/{id}")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        listingService.softDelete(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok("Listing removed");
    }

    @PostMapping("/listings/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<List<String>> uploadImages(@PathVariable UUID id,
                                                  @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.ok(listingImageService.addImages(SecurityUtils.currentUserId(), id, files));
    }

    @DeleteMapping("/listings/{id}/images/{imageId}")
    public ApiResponse<String> deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        listingImageService.deleteImage(SecurityUtils.currentUserId(), id, imageId);
        return ApiResponse.ok("Image deleted");
    }

    @GetMapping("/listings/{id}/availability")
    public ApiResponse<AvailabilityResponse> availability(@PathVariable UUID id) {
        return ApiResponse.ok(availabilityService.getAvailability(id));
    }

    @PostMapping("/listings/{id}/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AvailabilityResponse> blockDates(@PathVariable UUID id,
                                                        @Valid @RequestBody BlockDatesRequest request) {
        return ApiResponse.ok(availabilityService.blockDates(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/listings/{id}/availability/{rangeId}")
    public ApiResponse<String> unblockDates(@PathVariable UUID id, @PathVariable UUID rangeId) {
        availabilityService.unblockDates(SecurityUtils.currentUserId(), id, rangeId);
        return ApiResponse.ok("Blocked range removed");
    }

    @GetMapping("/users/{id}/listings")
    public ApiResponse<PageResponse<ListingSummaryResponse>> userListings(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok(listingService.publicListingsOf(id, pageable));
    }
}
