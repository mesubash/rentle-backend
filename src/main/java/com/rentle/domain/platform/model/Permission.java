package com.rentle.domain.platform.model;

import com.rentle.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission extends BaseEntity {

    @Column(name = "key", nullable = false, unique = true, length = 120)
    private String key;

    @Column(name = "domain", nullable = false, length = 40)
    private String domain;

    @Column(name = "resource", nullable = false, length = 40)
    private String resource;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "is_deprecated", nullable = false)
    private Boolean isDeprecated = false;
}
