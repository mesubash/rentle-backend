package com.rentle.domain.favorite.controller;

import com.rentle.domain.favorite.service.FavoriteService;
import com.rentle.domain.listing.dto.ListingSummaryResponse;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** Toggle a listing in the current user's saved list. */
    @PostMapping("/listings/{id}/favorite")
    public ApiResponse<Map<String, Boolean>> toggle(@PathVariable UUID id) {
        boolean saved = favoriteService.toggle(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok(Map.of("saved", saved));
    }

    /** The current user's saved listing ids (for filling the card hearts). */
    @GetMapping("/users/me/favorite-ids")
    public ApiResponse<List<UUID>> ids() {
        return ApiResponse.ok(favoriteService.savedIds(SecurityUtils.currentUserId()));
    }

    /** The current user's saved listings. */
    @GetMapping("/users/me/favorites")
    public ApiResponse<List<ListingSummaryResponse>> saved() {
        return ApiResponse.ok(favoriteService.saved(SecurityUtils.currentUserId()));
    }
}
