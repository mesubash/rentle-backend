package com.rentle.domain.organization.model;

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

/** A pending invite. On accept it becomes an IAM assignment at the org scope and this row is deleted. */
@Entity
@Table(name = "organization_invites")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
