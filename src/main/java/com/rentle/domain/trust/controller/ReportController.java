package com.rentle.domain.trust.controller;

import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.trust.dto.CreateReportRequest;
import com.rentle.domain.trust.dto.ReportResponse;
import com.rentle.domain.trust.dto.ResolveReportRequest;
import com.rentle.domain.trust.model.ReportStatus;
import com.rentle.domain.trust.service.ReportService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** File a report — any authenticated user. */
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportResponse> create(@Valid @RequestBody CreateReportRequest request) {
        return ApiResponse.ok(reportService.create(SecurityUtils.currentUserId(), request));
    }

    /** Admin report queue. */
    @GetMapping("/admin/reports")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TRUST_REPORT_READ + "')")
    public ApiResponse<PageResponse<ReportResponse>> list(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return ApiResponse.ok(reportService.list(status, pageable));
    }

    /** Resolve or dismiss a report. */
    @PutMapping("/admin/reports/{id}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.TRUST_REPORT_RESOLVE + "')")
    public ApiResponse<ReportResponse> resolve(@PathVariable UUID id,
                                               @Valid @RequestBody ResolveReportRequest request) {
        return ApiResponse.ok(reportService.resolve(
                SecurityUtils.currentUserId(), id, request.status(), request.note()));
    }
}
