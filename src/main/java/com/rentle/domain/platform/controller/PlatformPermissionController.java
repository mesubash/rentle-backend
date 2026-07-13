package com.rentle.domain.platform.controller;

import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.platform.dto.PermissionResponse;
import com.rentle.domain.platform.service.PlatformAdminService;
import com.rentle.shared.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform/permissions")
public class PlatformPermissionController {

    private final PlatformAdminService platformAdminService;

    public PlatformPermissionController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_PERMISSION_READ + "')")
    public ApiResponse<List<PermissionResponse>> permissions(
            @RequestParam(required = false) String domain) {
        return ApiResponse.ok(platformAdminService.permissions(domain));
    }
}
