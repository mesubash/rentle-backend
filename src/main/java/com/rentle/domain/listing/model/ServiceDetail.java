package com.rentle.domain.listing.model;

import com.rentle.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "service_details")
@Getter
@Setter
@NoArgsConstructor
public class ServiceDetail extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", unique = true, nullable = false)
    private Listing listing;

    @Column(name = "service_area_km")
    private Integer serviceAreaKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "typical_duration", length = 15)
    private ServiceDuration typicalDuration;

    @Column(name = "min_notice_hours", nullable = false)
    private Integer minNoticeHours = 24;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;
}
