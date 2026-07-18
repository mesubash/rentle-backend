package com.rentle.domain.organization.controller;

import com.rentle.domain.business.dto.WorkerRequest;
import com.rentle.domain.business.dto.WorkerResponse;
import com.rentle.domain.organization.dto.CreateOrgRequest;
import com.rentle.domain.organization.dto.InviteRequest;
import com.rentle.domain.organization.dto.InviteResponse;
import com.rentle.domain.organization.dto.MemberResponse;
import com.rentle.domain.organization.dto.OrgResponse;
import com.rentle.domain.organization.dto.OrgRoleResponse;
import com.rentle.domain.organization.dto.OrgSummaryResponse;
import com.rentle.domain.organization.dto.UpdateOrgRequest;
import com.rentle.domain.organization.service.OrganizationService;
import com.rentle.shared.api.ApiResponse;
import com.rentle.shared.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

/** Organizations: creation, membership, invites and the org worker registry. */
@RestController
@RequestMapping("/api/v1/orgs")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrgResponse> create(@Valid @RequestBody CreateOrgRequest request) {
        return ApiResponse.ok(organizationService.create(SecurityUtils.currentUserId(), request));
    }

    /** Organizations the current user belongs to — powers the account switcher. */
    @GetMapping("/me")
    public ApiResponse<List<OrgSummaryResponse>> myOrganizations() {
        return ApiResponse.ok(organizationService.myOrganizations(SecurityUtils.currentUserId()));
    }

    @PostMapping("/invites/{token}/accept")
    public ApiResponse<OrgResponse> acceptInvite(@PathVariable String token) {
        return ApiResponse.ok(organizationService.acceptInvite(SecurityUtils.currentUserId(), token));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrgResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(organizationService.get(SecurityUtils.currentUserId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<OrgResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateOrgRequest request) {
        return ApiResponse.ok(organizationService.update(SecurityUtils.currentUserId(), id, request));
    }

    @GetMapping("/{id}/assignable-roles")
    public ApiResponse<List<OrgRoleResponse>> assignableRoles(@PathVariable UUID id) {
        return ApiResponse.ok(organizationService.assignableRoles());
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<MemberResponse>> members(@PathVariable UUID id) {
        return ApiResponse.ok(organizationService.members(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/{id}/members/{memberUserId}")
    public ApiResponse<String> removeMember(@PathVariable UUID id, @PathVariable UUID memberUserId) {
        organizationService.removeMember(SecurityUtils.currentUserId(), id, memberUserId);
        return ApiResponse.ok("Member removed");
    }

    @GetMapping("/{id}/members/invites")
    public ApiResponse<List<InviteResponse>> listInvites(@PathVariable UUID id) {
        return ApiResponse.ok(organizationService.listInvites(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/members/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InviteResponse> invite(@PathVariable UUID id, @Valid @RequestBody InviteRequest request) {
        return ApiResponse.ok(organizationService.invite(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}/members/invites/{inviteId}")
    public ApiResponse<String> revokeInvite(@PathVariable UUID id, @PathVariable UUID inviteId) {
        organizationService.revokeInvite(SecurityUtils.currentUserId(), id, inviteId);
        return ApiResponse.ok("Invite revoked");
    }

    @GetMapping("/{id}/workers")
    public ApiResponse<List<WorkerResponse>> workers(@PathVariable UUID id) {
        return ApiResponse.ok(organizationService.workers(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/{id}/workers")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkerResponse> addWorker(@PathVariable UUID id, @Valid @RequestBody WorkerRequest request) {
        return ApiResponse.ok(organizationService.addWorker(SecurityUtils.currentUserId(), id, request));
    }

    @PutMapping("/{id}/workers/{workerId}")
    public ApiResponse<WorkerResponse> updateWorker(@PathVariable UUID id, @PathVariable UUID workerId,
                                                    @Valid @RequestBody WorkerRequest request) {
        return ApiResponse.ok(organizationService.updateWorker(SecurityUtils.currentUserId(), id, workerId, request));
    }

    @DeleteMapping("/{id}/workers/{workerId}")
    public ApiResponse<String> removeWorker(@PathVariable UUID id, @PathVariable UUID workerId) {
        organizationService.removeWorker(SecurityUtils.currentUserId(), id, workerId);
        return ApiResponse.ok("Worker removed");
    }
}
