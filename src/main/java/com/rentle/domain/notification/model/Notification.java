package com.rentle.domain.notification.model;

import com.rentle.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A persisted in-app notification for one user. Written at booking/KYC lifecycle
 * points; listed and marked read through the notifications API.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 40)
    private String type;      // e.g. BOOKING_REQUESTED, KYC_APPROVED

    @Column(nullable = false, length = 300)
    private String message;

    @Column(length = 300)
    private String link;      // in-app path, e.g. /bookings/{id}

    @Column(name = "is_read", nullable = false)
    private boolean read = false;
}
