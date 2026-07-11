package com.rentle.domain.user.model;

import com.rentle.shared.entity.AuditableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "kyc_details")
@Getter
@Setter
@NoArgsConstructor
public class KycDetail extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private KycStatus status = KycStatus.SUBMITTED;

    @Column(name = "real_name", nullable = false, length = 120)
    private String realName;

    @Column(name = "father_name", nullable = false, length = 120)
    private String fatherName;

    @Column(name = "grandfather_name", nullable = false, length = 120)
    private String grandfatherName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;

    @Column(name = "citizenship_number", nullable = false, length = 40)
    private String citizenshipNumber;

    @Column(name = "citizenship_issue_district", nullable = false, length = 60)
    private String citizenshipIssueDistrict;

    @Column(nullable = false, length = 80)
    private String occupation;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "district", column = @Column(name = "perm_district", length = 60)),
            @AttributeOverride(name = "municipality", column = @Column(name = "perm_municipality", length = 80)),
            @AttributeOverride(name = "ward", column = @Column(name = "perm_ward")),
            @AttributeOverride(name = "tole", column = @Column(name = "perm_tole", length = 120))
    })
    private Address permanentAddress;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "district", column = @Column(name = "temp_district", length = 60)),
            @AttributeOverride(name = "municipality", column = @Column(name = "temp_municipality", length = 80)),
            @AttributeOverride(name = "ward", column = @Column(name = "temp_ward")),
            @AttributeOverride(name = "tole", column = @Column(name = "temp_tole", length = 120))
    })
    private Address temporaryAddress;

    @Column(name = "front_image_ref", nullable = false, length = 300)
    private String frontImageRef;

    @Column(name = "back_image_ref", nullable = false, length = 300)
    private String backImageRef;

    @Column(name = "rejection_reason", length = 400)
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
