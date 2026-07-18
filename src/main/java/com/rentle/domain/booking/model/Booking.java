package com.rentle.domain.booking.model;

import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.user.model.User;
import com.rentle.shared.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renter_id", nullable = false)
    private User renter;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.REQUESTED;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "deposit_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(name = "deposit_paid", nullable = false)
    private Boolean depositPaid = false;

    @Column(name = "deposit_proof_url", length = 500)
    private String depositProofUrl;

    @Column(name = "renter_note", length = 500)
    private String renterNote;

    // Snapshot of the listing's rental terms accepted by the renter at request time.
    @Column(name = "agreed_terms", columnDefinition = "TEXT")
    private String agreedTerms;

    // Answers to this category's BOOKING field template (docs/12), validated on write.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private java.util.Map<String, Object> attributes = new java.util.HashMap<>();

    @Column(name = "attributes_template_version")
    private Integer attributesTemplateVersion;

    // Cancellation schedule in force at request time (docs/13), snapshotted for arbitration.
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "cancellation_schedule", columnDefinition = "jsonb")
    private java.util.List<com.rentle.domain.pricing.model.CancellationTier> cancellationSchedule;

    /** Set when the booked listing is org-owned; the org is the provider (fulfils this booking). */
    @Column(name = "provider_org_id")
    private UUID providerOrgId;

    // For org listings: which worker will attend (docs/07 Phase B). Name snapshotted.
    @Column(name = "assigned_worker_id")
    private UUID assignedWorkerId;

    @Column(name = "assigned_worker_name", length = 120)
    private String assignedWorkerName;

    // Condition evidence (private storage refs + notes) captured at hand-over and return,
    // so a deposit dispute has a record instead of being word-against-word.
    @Column(name = "checkout_condition_ref", length = 300)
    private String checkoutConditionRef;

    @Column(name = "checkout_note", length = 1000)
    private String checkoutNote;

    @Column(name = "return_condition_ref", length = 300)
    private String returnConditionRef;

    @Column(name = "return_note", length = 1000)
    private String returnNote;

    // Commission snapshot, frozen at completion (P0-3). 0 during the free-launch period.
    @Column(name = "platform_fee_percent", precision = 5, scale = 2)
    private java.math.BigDecimal platformFeePercent;

    @Column(name = "platform_fee_amount", precision = 10, scale = 2)
    private java.math.BigDecimal platformFeeAmount;

    @Column(name = "fee_invoiced", nullable = false)
    private boolean feeInvoiced = false;

    @Column(name = "fee_invoiced_at")
    private Instant feeInvoicedAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;
}
