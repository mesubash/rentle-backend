package com.rentle.domain.platform.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
public class PlatformSetting {

    @Id
    @Column(length = 60)
    private String key;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
