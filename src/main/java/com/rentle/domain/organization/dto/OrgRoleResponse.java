package com.rentle.domain.organization.dto;

import java.util.UUID;

/** An org role a member can be invited as (populates the invite role dropdown). */
public record OrgRoleResponse(UUID id, String name, String displayName, String description) {}
