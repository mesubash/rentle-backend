package com.rentle.domain.organization.service;

import com.rentle.domain.business.dto.WorkerRequest;
import com.rentle.domain.business.dto.WorkerResponse;
import com.rentle.domain.business.model.Worker;
import com.rentle.domain.business.repository.WorkerRepository;
import com.rentle.domain.listing.model.ListingStatus;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.domain.organization.dto.AdminOrgDetail;
import com.rentle.domain.organization.dto.AdminOrgRow;
import com.rentle.domain.organization.dto.CreateOrgRequest;
import com.rentle.domain.organization.dto.InviteRequest;
import com.rentle.domain.organization.dto.InviteResponse;
import com.rentle.domain.organization.dto.MemberResponse;
import com.rentle.domain.organization.dto.OrgResponse;
import com.rentle.domain.organization.dto.OrgRoleResponse;
import com.rentle.domain.organization.dto.OrgSummaryResponse;
import com.rentle.domain.organization.dto.UpdateOrgRequest;
import com.rentle.domain.organization.model.Organization;
import com.rentle.domain.organization.model.OrganizationInvite;
import com.rentle.domain.organization.repository.OrganizationInviteRepository;
import com.rentle.domain.organization.repository.OrganizationRepository;
import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.platform.catalog.RoleSeeds;
import com.rentle.domain.platform.model.Assignment;
import com.rentle.domain.platform.model.Role;
import com.rentle.domain.platform.model.Scope;
import com.rentle.domain.platform.model.ScopeType;
import com.rentle.domain.platform.repository.AssignmentRepository;
import com.rentle.domain.platform.repository.RoleRepository;
import com.rentle.domain.platform.repository.ScopeRepository;
import com.rentle.domain.platform.service.PermissionResolverService;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.exception.UnauthorizedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Organizations, their membership (IAM-scoped assignments), invites and worker registry. */
@Service
public class OrganizationService {

    private static final List<String> ORG_ROLE_NAMES = List.of(RoleSeeds.ORG_OWNER, RoleSeeds.ORG_ADMIN, RoleSeeds.ORG_STAFF);

    private final OrganizationRepository orgRepository;
    private final OrganizationInviteRepository inviteRepository;
    private final WorkerRepository workerRepository;
    private final ListingRepository listingRepository;
    private final ScopeRepository scopeRepository;
    private final RoleRepository roleRepository;
    private final AssignmentRepository assignmentRepository;
    private final PermissionResolverService permissionResolver;
    private final UserRepository userRepository;

    public OrganizationService(OrganizationRepository orgRepository,
                               OrganizationInviteRepository inviteRepository,
                               WorkerRepository workerRepository,
                               ListingRepository listingRepository,
                               ScopeRepository scopeRepository,
                               RoleRepository roleRepository,
                               AssignmentRepository assignmentRepository,
                               PermissionResolverService permissionResolver,
                               UserRepository userRepository) {
        this.orgRepository = orgRepository;
        this.inviteRepository = inviteRepository;
        this.workerRepository = workerRepository;
        this.listingRepository = listingRepository;
        this.scopeRepository = scopeRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.permissionResolver = permissionResolver;
        this.userRepository = userRepository;
    }

    // ---- admin console (platform oversight of all organizations) ------------

    @Transactional(readOnly = true)
    public PageResponse<AdminOrgRow> adminList(String search, Pageable pageable) {
        Page<Organization> page = (search == null || search.isBlank())
                ? orgRepository.findAllByOrderByCreatedAtDesc(pageable)
                : orgRepository.findByNameContainingIgnoreCaseOrderByCreatedAtDesc(search.trim(), pageable);
        return PageResponse.from(page, org -> new AdminOrgRow(
                org.getId(), org.getName(), org.getSlug(), org.getLogoUrl(),
                assignmentRepository.countByScopeIdAndRevokedAtIsNull(org.getScopeId()),
                listingRepository.countByOrgIdAndStatusNot(org.getId(), ListingStatus.REMOVED),
                org.getCreatedAt()));
    }

    @Transactional(readOnly = true)
    public AdminOrgDetail adminGet(UUID orgId) {
        Organization org = requireOrg(orgId);
        List<MemberResponse> members = assignmentRepository.findMembers(org.getScopeId()).stream().map(a -> {
            User u = a.getSubject();
            Role r = a.getRole();
            return new MemberResponse(a.getId(), u.getId(), u.getFullName(), u.getEmail(),
                    r.getId(), r.getName(), r.getDisplayName());
        }).toList();
        return new AdminOrgDetail(org.getId(), org.getName(), org.getSlug(), org.getBio(), org.getLogoUrl(),
                org.getCreatedBy(), org.getCreatedAt(),
                listingRepository.countByOrgIdAndStatusNot(org.getId(), ListingStatus.REMOVED), members);
    }

    // ---- organizations ------------------------------------------------------

    @Transactional
    public OrgResponse create(UUID userId, CreateOrgRequest req) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Scope root = scopeRepository.findFirstByType(ScopeType.ROOT)
                .orElseThrow(() -> new RentleException("Platform scope is not initialised"));
        Scope scope = new Scope();
        scope.setType(ScopeType.ORG);
        scope.setName(req.name().trim());
        scope.setParent(root);
        scope = scopeRepository.save(scope);

        Organization org = new Organization();
        org.setName(req.name().trim());
        org.setSlug(uniqueSlug(req.name()));
        org.setBio(req.bio());
        org.setLogoUrl(req.logoUrl());
        org.setScopeId(scope.getId());
        org.setCreatedBy(userId);
        org = orgRepository.save(org);

        grant(creator, orgRole(RoleSeeds.ORG_OWNER), scope, creator);
        return OrgResponse.from(org, orgPermissions(userId, scope.getId()));
    }

    @Transactional(readOnly = true)
    public List<OrgSummaryResponse> myOrganizations(UUID userId) {
        List<UUID> scopeIds = assignmentRepository.findLiveOrgScopeIds(userId);
        if (scopeIds.isEmpty()) return List.of();
        return orgRepository.findByScopeIdInOrderByNameAsc(scopeIds).stream().map(OrgSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrgResponse get(UUID userId, UUID orgId) {
        Organization org = requireMember(userId, orgId);
        return OrgResponse.from(org, orgPermissions(userId, org.getScopeId()));
    }

    @Transactional
    public OrgResponse update(UUID userId, UUID orgId, UpdateOrgRequest req) {
        Organization org = requirePermission(userId, orgId, PermissionKeys.ORGANIZATION_ORG_MANAGE);
        org.setName(req.name().trim());
        org.setBio(req.bio());
        org.setLogoUrl(req.logoUrl());
        return OrgResponse.from(orgRepository.save(org), orgPermissions(userId, org.getScopeId()));
    }

    // ---- members ------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MemberResponse> members(UUID userId, UUID orgId) {
        Organization org = requireMember(userId, orgId);
        return assignmentRepository.findMembers(org.getScopeId()).stream().map(a -> {
            User u = a.getSubject();
            Role r = a.getRole();
            return new MemberResponse(a.getId(), u.getId(), u.getFullName(), u.getEmail(),
                    r.getId(), r.getName(), r.getDisplayName());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<OrgRoleResponse> assignableRoles() {
        return roleRepository.findAllByOrderByNameAsc().stream()
                .filter(r -> ORG_ROLE_NAMES.contains(r.getName()))
                .map(r -> new OrgRoleResponse(r.getId(), r.getName(), r.getDisplayName(), r.getDescription()))
                .toList();
    }

    @Transactional
    public void removeMember(UUID actorId, UUID orgId, UUID memberUserId) {
        Organization org = requirePermission(actorId, orgId, PermissionKeys.ORGANIZATION_MEMBER_MANAGE);
        List<Assignment> assignments = assignmentRepository
                .findBySubjectIdAndScopeIdAndRevokedAtIsNull(memberUserId, org.getScopeId());
        if (assignments.isEmpty()) throw new ResourceNotFoundException("Member not found");

        Role ownerRole = orgRole(RoleSeeds.ORG_OWNER);
        boolean removingOwner = assignments.stream().anyMatch(a -> a.getRole().getId().equals(ownerRole.getId()));
        if (removingOwner && assignmentRepository.countByScopeIdAndRoleIdAndRevokedAtIsNull(org.getScopeId(), ownerRole.getId()) <= 1) {
            throw new RentleException("An organization must keep at least one owner");
        }
        User actor = userRepository.findById(actorId).orElseThrow();
        assignments.forEach(a -> { a.setRevokedAt(java.time.Instant.now()); a.setRevokedBy(actor); });
        assignmentRepository.saveAll(assignments);
    }

    // ---- invites ------------------------------------------------------------

    @Transactional
    public InviteResponse invite(UUID actorId, UUID orgId, InviteRequest req) {
        Organization org = requirePermission(actorId, orgId, PermissionKeys.ORGANIZATION_MEMBER_MANAGE);
        Role role = roleRepository.findById(req.roleId())
                .filter(r -> ORG_ROLE_NAMES.contains(r.getName()))
                .orElseThrow(() -> new RentleException("That role cannot be assigned to an organization member"));

        String email = req.email().trim().toLowerCase(Locale.ROOT);
        userRepository.findByEmailIgnoreCase(email).ifPresent(u -> {
            if (assignmentRepository.existsBySubjectIdAndScopeIdAndRevokedAtIsNull(u.getId(), org.getScopeId())) {
                throw new RentleException("That person is already a member");
            }
        });
        inviteRepository.findByOrgIdAndEmailIgnoreCase(orgId, email)
                .ifPresent(existing -> inviteRepository.delete(existing));

        OrganizationInvite invite = new OrganizationInvite();
        invite.setOrgId(orgId);
        invite.setEmail(email);
        invite.setRoleId(role.getId());
        invite.setToken(UUID.randomUUID().toString().replace("-", ""));
        invite.setInvitedBy(actorId);
        invite = inviteRepository.save(invite);
        return new InviteResponse(invite.getId(), invite.getEmail(), role.getId(), role.getDisplayName(),
                invite.getToken(), invite.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<InviteResponse> listInvites(UUID actorId, UUID orgId) {
        Organization org = requirePermission(actorId, orgId, PermissionKeys.ORGANIZATION_MEMBER_MANAGE);
        return inviteRepository.findByOrgIdOrderByCreatedAtAsc(org.getId()).stream().map(i -> {
            Role role = roleRepository.findById(i.getRoleId()).orElse(null);
            return new InviteResponse(i.getId(), i.getEmail(), i.getRoleId(),
                    role == null ? null : role.getDisplayName(), i.getToken(), i.getCreatedAt());
        }).toList();
    }

    @Transactional
    public void revokeInvite(UUID actorId, UUID orgId, UUID inviteId) {
        requirePermission(actorId, orgId, PermissionKeys.ORGANIZATION_MEMBER_MANAGE);
        OrganizationInvite invite = inviteRepository.findById(inviteId)
                .filter(i -> i.getOrgId().equals(orgId))
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));
        inviteRepository.delete(invite);
    }

    /** The current user accepts an invite: becomes a member (an assignment at the org scope). */
    @Transactional
    public OrgResponse acceptInvite(UUID userId, String token) {
        OrganizationInvite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("This invite is not valid"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!user.getEmail().equalsIgnoreCase(invite.getEmail())) {
            throw new UnauthorizedException("This invite was sent to a different email address");
        }
        Organization org = orgRepository.findById(invite.getOrgId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        Scope scope = scopeRepository.findById(org.getScopeId()).orElseThrow();
        Role role = roleRepository.findById(invite.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        if (!assignmentRepository.existsBySubjectIdAndScopeIdAndRevokedAtIsNull(userId, scope.getId())) {
            grant(user, role, scope, user);
        }
        inviteRepository.delete(invite);
        return OrgResponse.from(org, orgPermissions(userId, scope.getId()));
    }

    // ---- worker registry (org-scoped) --------------------------------------

    @Transactional(readOnly = true)
    public List<WorkerResponse> workers(UUID userId, UUID orgId) {
        Organization org = requireMember(userId, orgId);
        return workerRepository.findByOrgIdOrderByCreatedAtAsc(org.getId()).stream()
                .filter(Worker::isActive).map(WorkerResponse::from).toList();
    }

    @Transactional
    public WorkerResponse addWorker(UUID userId, UUID orgId, WorkerRequest req) {
        Organization org = requirePermission(userId, orgId, PermissionKeys.ORGANIZATION_WORKER_MANAGE);
        Worker w = new Worker();
        w.setOrgId(org.getId());
        w.setName(req.name().trim());
        w.setPhone(req.phone());
        w.setRole(req.role());
        return WorkerResponse.from(workerRepository.save(w));
    }

    @Transactional
    public WorkerResponse updateWorker(UUID userId, UUID orgId, UUID workerId, WorkerRequest req) {
        Organization org = requirePermission(userId, orgId, PermissionKeys.ORGANIZATION_WORKER_MANAGE);
        Worker w = ownedWorker(org.getId(), workerId);
        w.setName(req.name().trim());
        w.setPhone(req.phone());
        w.setRole(req.role());
        return WorkerResponse.from(workerRepository.save(w));
    }

    @Transactional
    public void removeWorker(UUID userId, UUID orgId, UUID workerId) {
        Organization org = requirePermission(userId, orgId, PermissionKeys.ORGANIZATION_WORKER_MANAGE);
        Worker w = ownedWorker(org.getId(), workerId);
        w.setActive(false);
        workerRepository.save(w);
    }

    // ---- shared authorization helpers (used here and by listing/booking) ----

    /** True when the user holds the given org-scoped permission. Callers outside this service
     *  (listing/booking ownership) use this to treat "acting as the org" like ownership. */
    @Transactional(readOnly = true)
    public boolean hasOrgPermission(UUID userId, UUID orgId, String permissionKey) {
        Organization org = orgRepository.findById(orgId).orElse(null);
        return org != null && permissionResolver.hasPermissionInScope(userId, org.getScopeId(), permissionKey);
    }

    public Organization requireOrg(UUID orgId) {
        return orgRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    private Organization requireMember(UUID userId, UUID orgId) {
        Organization org = requireOrg(orgId);
        if (!assignmentRepository.existsBySubjectIdAndScopeIdAndRevokedAtIsNull(userId, org.getScopeId())) {
            throw new UnauthorizedException("You are not a member of this organization");
        }
        return org;
    }

    private Organization requirePermission(UUID userId, UUID orgId, String permissionKey) {
        Organization org = requireOrg(orgId);
        if (!permissionResolver.hasPermissionInScope(userId, org.getScopeId(), permissionKey)) {
            throw new UnauthorizedException("You do not have permission to do this in this organization");
        }
        return org;
    }

    private List<String> orgPermissions(UUID userId, UUID scopeId) {
        return assignmentRepository.findLivePermissionKeysInScope(userId, scopeId).stream().sorted().toList();
    }

    private Worker ownedWorker(UUID orgId, UUID workerId) {
        return workerRepository.findByIdAndOrgId(workerId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Worker not found"));
    }

    private Role orgRole(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new RentleException("Org role " + name + " is not seeded"));
    }

    private void grant(User subject, Role role, Scope scope, User grantedBy) {
        if (assignmentRepository.existsBySubjectIdAndRoleIdAndScopeIdAndRevokedAtIsNull(subject.getId(), role.getId(), scope.getId())) {
            return;
        }
        Assignment a = new Assignment();
        a.setSubject(subject);
        a.setRole(role);
        a.setScope(scope);
        a.setGrantedBy(grantedBy);
        assignmentRepository.save(a);
    }

    private String uniqueSlug(String name) {
        String base = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        if (base.isEmpty()) base = "org";
        if (base.length() > 120) base = base.substring(0, 120);
        String slug = base;
        while (orgRepository.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }
}
