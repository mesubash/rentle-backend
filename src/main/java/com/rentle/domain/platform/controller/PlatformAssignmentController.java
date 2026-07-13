package com.rentle.domain.platform.controller;

import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.platform.dto.AssignmentResponse;
import com.rentle.domain.platform.dto.CreateAssignmentRequest;
import com.rentle.domain.platform.service.PlatformAdminService;
import com.rentle.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/assignments")
public class PlatformAssignmentController {

    private final PlatformAdminService platformAdminService;

    public PlatformAssignmentController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_ASSIGNMENT_READ + "')")
    public ApiResponse<List<AssignmentResponse>> assignments(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID roleId) {
        return ApiResponse.ok(platformAdminService.assignments(userId, roleId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_ASSIGNMENT_MANAGE + "')")
    public ApiResponse<AssignmentResponse> create(@Valid @RequestBody CreateAssignmentRequest request) {
        return ApiResponse.ok(platformAdminService.createAssignment(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_ASSIGNMENT_MANAGE + "')")
    public ApiResponse<String> revoke(@PathVariable UUID id) {
        platformAdminService.revokeAssignment(id);
        return ApiResponse.ok("Assignment revoked");
    }
}
