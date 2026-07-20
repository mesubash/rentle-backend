package com.rentle.domain.business.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A worker registered under a BUSINESS account, who may attend a booking (docs/07 Phase B). */
@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 80)
    private String role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
