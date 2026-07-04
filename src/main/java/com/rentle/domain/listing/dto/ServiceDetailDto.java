package com.rentle.domain.listing.dto;

import com.rentle.domain.listing.model.ServiceDetail;
import com.rentle.domain.listing.model.ServiceDuration;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ServiceDetailDto(
        @Min(1) Integer serviceAreaKm,
        ServiceDuration typicalDuration,
        @Min(0) Integer minNoticeHours,
        @Size(max = 500) String portfolioUrl
) {
    public static ServiceDetailDto from(ServiceDetail d) {
        return new ServiceDetailDto(d.getServiceAreaKm(), d.getTypicalDuration(),
                d.getMinNoticeHours(), d.getPortfolioUrl());
    }
}
