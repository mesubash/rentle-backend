package com.rentle.domain.platform.controller;

import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.platform.dto.CreateRoleRequest;
import com.rentle.domain.platform.dto.RoleResponse;
import com.rentle.domain.platform.dto.UpdateRoleRequest;
import com.rentle.domain.platform.service.PlatformAdminService;
import com.rentle.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/roles")
public class PlatformRoleController {

    private final PlatformAdminService platformAdminService;

    public PlatformRoleController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_ROLE_READ + "')")
    public ApiResponse<List<RoleResponse>> roles() {
        return ApiResponse.ok(platformAdminService.roles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_ROLE_READ + "')")
    public ApiResponse<RoleResponse> role(@PathVariable UUID id) {
        return ApiResponse.ok(platformAdminService.role(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_ROLE_MANAGE + "')")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(platformAdminService.createRole(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_ROLE_MANAGE + "')")
    public ApiResponse<RoleResponse> update(@PathVariable UUID id,
                                            @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok(platformAdminService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_ROLE_MANAGE + "')")
    public ApiResponse<String> delete(@PathVariable UUID id) {
        platformAdminService.deleteRole(id);
        return ApiResponse.ok("Role deleted");
    }
}
