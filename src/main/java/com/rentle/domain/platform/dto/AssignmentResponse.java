package com.rentle.domain.platform.dto;

import com.rentle.domain.platform.model.Assignment;

import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        UUID userId,
        String email,
        String fullName,
        UUID roleId,
        String roleName,
        UUID scopeId,
        String scopeName,
        UUID grantedBy,
        Instant createdAt
) {
    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getSubject().getId(),
                assignment.getSubject().getEmail(),
                assignment.getSubject().getFullName(),
                assignment.getRole().getId(),
                assignment.getRole().getName(),
                assignment.getScope().getId(),
                assignment.getScope().getName(),
                assignment.getGrantedBy() == null ? null : assignment.getGrantedBy().getId(),
                assignment.getCreatedAt()
        );
    }
}
