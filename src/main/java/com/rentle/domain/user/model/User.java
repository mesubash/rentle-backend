package com.rentle.domain.user.model;

import com.rentle.shared.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditableEntity {

    // Nullable: Google sign-ups have no phone until they add one.
    @Column(name = "phone_number", unique = true, length = 20)
    private String phoneNumber;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    // Nullable: social-login accounts have no local password.
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "google_id", unique = true, length = 64)
    private String googleId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    // Owner's eSewa/Khalti handle, shown to a renter at the deposit step so they know
    // where to send the deposit. Free text (owners use different wallets).
    @Column(name = "payment_wallet", length = 100)
    private String paymentWallet;

    // Business/org accounts (docs/07 Phase B): a BUSINESS lists as a company and registers workers.
    @Column(name = "account_type", nullable = false, length = 12)
    private String accountType = "INDIVIDUAL";

    @Column(name = "business_name", length = 120)
    private String businessName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "phone_verified", nullable = false)
    private Boolean phoneVerified = false;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "citizenship_card_url", length = 500)
    private String citizenshipCardUrl;

    @Column(name = "citizenship_verified", nullable = false)
    private Boolean citizenshipVerified = false;

    @Column(name = "trust_score", precision = 3, scale = 2)
    private BigDecimal trustScore;

    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
