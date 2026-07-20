package com.rentle.domain.listing.service;

import com.rentle.domain.listing.dto.ListingSummaryResponse;
import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.ListingType;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.shared.api.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class ListingSearchService {

    private static final Set<String> VALID_SORTS = Set.of("newest", "price_asc", "price_desc", "rating");

    private final ListingRepository listingRepository;
    private final ListingService listingService;

    public ListingSearchService(ListingRepository listingRepository, ListingService listingService) {
        this.listingRepository = listingRepository;
        this.listingService = listingService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> search(String q,
                                                       ListingType type,
                                                       UUID categoryId,
                                                       String district,
                                                       java.math.BigDecimal minPrice,
                                                       java.math.BigDecimal maxPrice,
                                                       String sort,
                                                       Pageable pageable) {
        String normalizedSort = (sort != null && VALID_SORTS.contains(sort)) ? sort : "newest";
        Page<Listing> page = listingRepository.search(
                blankToNull(q),
                type != null ? type.name() : null,
                categoryId != null ? categoryId.toString() : null,
                blankToNull(district),
                minPrice,
                maxPrice,
                normalizedSort,
                pageable);
        return listingService.toSummaryPage(page);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
