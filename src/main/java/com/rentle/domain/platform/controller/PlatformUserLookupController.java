package com.rentle.domain.platform.controller;

import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.platform.dto.UserLookupResponse;
import com.rentle.domain.platform.service.PlatformAdminService;
import com.rentle.shared.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/users")
public class PlatformUserLookupController {

    private final PlatformAdminService platformAdminService;

    public PlatformUserLookupController(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('" + PermissionKeys.IDENTITY_USER_READ + "')")
    public ApiResponse<UserLookupResponse> lookup(@RequestParam String email) {
        return ApiResponse.ok(platformAdminService.lookupUser(email));
    }
}
