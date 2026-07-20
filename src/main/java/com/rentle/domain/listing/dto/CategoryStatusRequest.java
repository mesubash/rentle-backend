package com.rentle.domain.listing.dto;

import jakarta.validation.constraints.NotNull;

public record CategoryStatusRequest(@NotNull Boolean active) {}
