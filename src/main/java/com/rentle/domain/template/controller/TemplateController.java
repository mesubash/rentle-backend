package com.rentle.domain.template.controller;

import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.template.dto.SaveTemplateRequest;
import com.rentle.domain.template.dto.TemplateResponse;
import com.rentle.domain.template.model.TemplateScope;
import com.rentle.domain.template.service.FieldTemplateService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TemplateController {

    private final FieldTemplateService templateService;

    public TemplateController(FieldTemplateService templateService) {
        this.templateService = templateService;
    }

    /** Public: the current template for a category+scope, to render its form. */
    @GetMapping("/categories/{id}/templates/{scope}")
    public ApiResponse<TemplateResponse> current(@PathVariable UUID id, @PathVariable TemplateScope scope) {
        return ApiResponse.ok(templateService.current(id, scope).map(TemplateResponse::from).orElse(null));
    }

    /** Admin: all templates configured on a category (any scope, current versions and history). */
    @GetMapping("/admin/categories/{id}/templates")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LISTING_CATEGORY_MANAGE + "')")
    public ApiResponse<List<TemplateResponse>> forCategory(@PathVariable UUID id) {
        return ApiResponse.ok(templateService.forCategory(id).stream().map(TemplateResponse::from).toList());
    }

    /** Admin: save a new version of a category+scope template. */
    @PutMapping("/admin/categories/{id}/templates/{scope}")
    @PreAuthorize("hasAuthority('" + PermissionKeys.LISTING_CATEGORY_MANAGE + "')")
    public ApiResponse<TemplateResponse> save(@PathVariable UUID id, @PathVariable TemplateScope scope,
                                              @Valid @RequestBody SaveTemplateRequest request) {
        return ApiResponse.ok(TemplateResponse.from(
                templateService.saveNewVersion(id, scope, request.fields(), SecurityUtils.currentUserId())));
    }
}
