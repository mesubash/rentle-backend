package com.rentle.domain.platform.model;

import com.rentle.shared.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends AuditableEntity {

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "is_system_role", nullable = false)
    private Boolean isSystemRole = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_scope_id")
    private Scope ownerScope;
}
