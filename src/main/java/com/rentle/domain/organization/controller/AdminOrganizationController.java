package com.rentle.domain.organization.controller;

import com.rentle.domain.organization.dto.AdminOrgDetail;
import com.rentle.domain.organization.dto.AdminOrgRow;
import com.rentle.domain.organization.service.OrganizationService;
import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin console: companies lookup and organization detail (platform oversight). */
@RestController
@RequestMapping("/api/v1/platform/organizations")
@PreAuthorize("hasAuthority('" + PermissionKeys.PLATFORM_ORGANIZATION_READ + "')")
public class AdminOrganizationController {

    private final OrganizationService organizationService;

    public AdminOrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminOrgRow>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ApiResponse.ok(organizationService.adminList(q, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminOrgDetail> get(@PathVariable UUID id) {
        return ApiResponse.ok(organizationService.adminGet(id));
    }
}
