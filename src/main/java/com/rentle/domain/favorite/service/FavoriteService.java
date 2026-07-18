package com.rentle.domain.favorite.service;

import com.rentle.domain.favorite.model.Favorite;
import com.rentle.domain.favorite.repository.FavoriteRepository;
import com.rentle.domain.listing.dto.ListingSummaryResponse;
import com.rentle.domain.listing.service.ListingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FavoriteService {

    private final FavoriteRepository repository;
    private final ListingService listingService;

    public FavoriteService(FavoriteRepository repository, ListingService listingService) {
        this.repository = repository;
        this.listingService = listingService;
    }

    /** Toggle a listing in the user's saved list. Returns true if now saved. */
    @Transactional
    public boolean toggle(UUID userId, UUID listingId) {
        return repository.findByUserIdAndListingId(userId, listingId)
                .map(existing -> { repository.delete(existing); return false; })
                .orElseGet(() -> {
                    Favorite f = new Favorite();
                    f.setUserId(userId);
                    f.setListingId(listingId);
                    repository.save(f);
                    return true;
                });
    }

    @Transactional(readOnly = true)
    public List<UUID> savedIds(UUID userId) {
        return repository.listingIds(userId);
    }

    @Transactional(readOnly = true)
    public List<ListingSummaryResponse> saved(UUID userId) {
        return listingService.summariesByIds(
                repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(Favorite::getListingId).toList());
    }
}
