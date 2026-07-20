package com.rentle.domain.demo;

import com.rentle.domain.business.dto.WorkerRequest;
import com.rentle.domain.listing.dto.CreateListingRequest;
import com.rentle.domain.listing.dto.ListingResponse;
import com.rentle.domain.listing.dto.ProductDetailDto;
import com.rentle.domain.listing.dto.ServiceDetailDto;
import com.rentle.domain.listing.dto.UpdateListingRequest;
import com.rentle.domain.listing.model.ItemCondition;
import com.rentle.domain.listing.model.ListingStatus;
import com.rentle.domain.listing.model.ListingType;
import com.rentle.domain.listing.model.PriceUnit;
import com.rentle.domain.listing.model.ServiceDuration;
import com.rentle.domain.listing.repository.CategoryRepository;
import com.rentle.domain.listing.service.ListingService;
import com.rentle.domain.organization.dto.CreateOrgRequest;
import com.rentle.domain.organization.dto.InviteRequest;
import com.rentle.domain.organization.dto.OrgResponse;
import com.rentle.domain.organization.service.OrganizationService;
import com.rentle.domain.platform.catalog.RoleSeeds;
import com.rentle.domain.platform.model.Assignment;
import com.rentle.domain.platform.model.Role;
import com.rentle.domain.platform.model.Scope;
import com.rentle.domain.platform.model.ScopeType;
import com.rentle.domain.platform.repository.AssignmentRepository;
import com.rentle.domain.platform.repository.RoleRepository;
import com.rentle.domain.platform.repository.ScopeRepository;
import com.rentle.domain.template.model.FieldDefinition;
import com.rentle.domain.template.model.FieldType;
import com.rentle.domain.template.model.TemplateScope;
import com.rentle.domain.template.service.FieldTemplateService;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.domain.verification.dto.ProviderVerificationResponse;
import com.rentle.domain.verification.dto.SubmitVerificationRequest;
import com.rentle.domain.verification.service.ProviderVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds a small, authentic-looking marketplace so a fresh install isn't empty: an individual
 * service provider, two service companies (with owner/admin/staff members and workers), and a
 * spread of active listings. Idempotent — skips entirely once the demo provider exists.
 *
 * Runs after {@code IamCatalogSynchronizer} (@Order(1)) so roles and the ROOT scope already exist.
 * Demo accounts share the password below; see V033__seeded_accounts.sql for the full list.
 */
@Component
@ConditionalOnProperty(name = "rentle.demo-seed", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DemoDataSeeder {

    private static final String PASSWORD = "Rentle@123";
    private static final String CITY = "Kathmandu";

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final OrganizationService orgService;
    private final ListingService listingService;
    private final ProviderVerificationService pvService;
    private final FieldTemplateService templateService;
    private final CategoryRepository categoryRepo;
    private final RoleRepository roleRepo;
    private final ScopeRepository scopeRepo;
    private final AssignmentRepository assignmentRepo;

    private int phoneSeq = 1;

    public DemoDataSeeder(UserRepository userRepo, PasswordEncoder encoder, OrganizationService orgService,
                          ListingService listingService, ProviderVerificationService pvService,
                          FieldTemplateService templateService, CategoryRepository categoryRepo,
                          RoleRepository roleRepo, ScopeRepository scopeRepo, AssignmentRepository assignmentRepo) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.orgService = orgService;
        this.listingService = listingService;
        this.pvService = pvService;
        this.templateService = templateService;
        this.categoryRepo = categoryRepo;
        this.roleRepo = roleRepo;
        this.scopeRepo = scopeRepo;
        this.assignmentRepo = assignmentRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    @Transactional
    public void seed() {
        if (userRepo.findByEmailIgnoreCase("provider@rentle.online").isPresent()) {
            return;   // already seeded
        }
        User admin = userRepo.findByEmailIgnoreCase("admin@rentle.online").orElse(null);
        if (admin == null) {
            log.warn("Demo seeder skipped: admin@rentle.online not found");
            return;
        }
        UUID adminId = admin.getId();
        log.info("Seeding demo marketplace data…");

        // Licensed trades (home services) require provider verification — seed that template.
        seedVerificationTemplate("home-services", adminId);

        // 1) Individual service provider who also rents out his own gear.
        User provider = user("provider@rentle.online", "Bikash Sharma");
        service(provider.getId(), null, "event-photography", "Wedding & event photography",
                "Full-day event coverage with edited photos delivered within a week.", "12000", PriceUnit.FLAT,
                new ServiceDetailDto(15, ServiceDuration.FULL_DAY, 48, "https://portfolio.example/bikash"));
        product(provider.getId(), null, "cameras-tech", "Sony A7 III camera kit",
                "Full-frame mirrorless body with a 24-70mm lens, two batteries and a charger.", "1800",
                new ProductDetailDto(ItemCondition.GOOD, "Sony", "A7 III", 1, 14));
        product(provider.getId(), null, "cameras-tech", "DJI Mini 3 drone",
                "Lightweight 4K drone with three batteries. Great for travel and events.", "1500",
                new ProductDetailDto(ItemCondition.GOOD, "DJI", "Mini 3", 1, 7));
        product(provider.getId(), null, "bikes-scooters", "Trek mountain bike",
                "27.5\" hardtail, recently serviced. Helmet included.", "700",
                new ProductDetailDto(ItemCondition.GOOD, "Trek", "Marlin 5", 1, 30));
        product(provider.getId(), null, "outdoor-camping", "4-person camping tent",
                "Waterproof dome tent with a carry bag. Sleeps four comfortably.", "500",
                new ProductDetailDto(ItemCondition.GOOD, "Quechua", "MH100", 1, 21));

        // 2) Everest Movers — a moving company with a team and workers.
        User everestOwner = user("ever.owner@rentle.online", "Suresh Thapa");
        OrgResponse everest = orgService.create(everestOwner.getId(),
                new CreateOrgRequest("Everest Movers", "House and office relocation across the valley.", null));
        addMember(everestOwner.getId(), everest.id(), user("ever.admin@rentle.online", "Anjali Gurung"), RoleSeeds.ORG_ADMIN);
        addMember(everestOwner.getId(), everest.id(), user("ever.staff@rentle.online", "Dipesh Rai"), RoleSeeds.ORG_STAFF);
        worker(everestOwner.getId(), everest.id(), "Ram Bahadur", "Driver");
        worker(everestOwner.getId(), everest.id(), "Shyam Lama", "Mover");
        worker(everestOwner.getId(), everest.id(), "Hari Magar", "Mover");
        // A still-pending invite, to exercise the accept-invite flow.
        orgService.invite(everestOwner.getId(), everest.id(),
                new InviteRequest("ever.invitee@rentle.online", orgRoleId(RoleSeeds.ORG_STAFF)));
        service(everestOwner.getId(), everest.id(), "moving-transport", "Full house shifting",
                "Packing, loading, transport and unloading with an experienced team and truck.", "8000", PriceUnit.FLAT,
                new ServiceDetailDto(30, ServiceDuration.FULL_DAY, 24, null));
        service(everestOwner.getId(), everest.id(), "moving-transport", "Office relocation",
                "After-hours office moves with careful handling of equipment.", "15000", PriceUnit.FLAT,
                new ServiceDetailDto(30, ServiceDuration.FULL_DAY, 48, null));
        product(everestOwner.getId(), everest.id(), "tools-equipment", "Moving trolley & packing kit",
                "Heavy-duty trolley, moving blankets and 20 packing boxes.", "600",
                new ProductDetailDto(ItemCondition.GOOD, null, null, 1, 30));

        // 3) Kathmandu Plumbers — a licensed trade, verified in a gated category.
        User plumberOwner = user("ktm.owner@rentle.online", "Rajan Shrestha");
        OrgResponse plumbers = orgService.create(plumberOwner.getId(),
                new CreateOrgRequest("Kathmandu Plumbers", "Licensed plumbing and electrical work.", null));
        worker(plumberOwner.getId(), plumbers.id(), "Gopal Tamang", "Plumber");
        approveVerification(plumberOwner.getId(), plumbers.id(), "home-services", adminId);
        service(plumberOwner.getId(), plumbers.id(), "home-services", "Emergency plumbing",
                "Same-day leak and blockage repairs by licensed plumbers.", "1000", PriceUnit.PER_HOUR,
                new ServiceDetailDto(20, ServiceDuration.HOURLY, 2, null));
        service(plumberOwner.getId(), plumbers.id(), "home-services", "Electrical wiring & repair",
                "Certified electrical installation and fault-finding.", "1200", PriceUnit.PER_HOUR,
                new ServiceDetailDto(20, ServiceDuration.HOURLY, 4, null));

        // 4) Platform staff (to exercise non-super-admin roles).
        grantPlatform(user("kyc@rentle.online", "Priya KYC Reviewer"), "KYC_REVIEWER");
        grantPlatform(user("support@rentle.online", "Sita Support"), "SUPPORT");

        log.info("Demo marketplace data seeded.");
    }

    // ---- helpers ------------------------------------------------------------

    private User user(String email, String name) {
        return userRepo.findByEmailIgnoreCase(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName(name);
            u.setPasswordHash(encoder.encode(PASSWORD));
            u.setPhoneNumber(String.format("+977980%06d", phoneSeq++));
            u.setStatus(UserStatus.VERIFIED);
            u.setPhoneVerified(true);
            u.setEmailVerified(true);
            u.setCitizenshipVerified(true);
            return userRepo.save(u);
        });
    }

    private UUID orgRoleId(String roleName) {
        return orgService.assignableRoles().stream()
                .filter(r -> r.name().equals(roleName)).findFirst().orElseThrow().id();
    }

    private void addMember(UUID ownerId, UUID orgId, User member, String roleName) {
        var invite = orgService.invite(ownerId, orgId, new InviteRequest(member.getEmail(), orgRoleId(roleName)));
        orgService.acceptInvite(member.getId(), invite.token());
    }

    private void worker(UUID ownerId, UUID orgId, String name, String role) {
        orgService.addWorker(ownerId, orgId, new WorkerRequest(name, null, role));
    }

    private void grantPlatform(User user, String roleName) {
        Role role = roleRepo.findByName(roleName).orElseThrow();
        Scope root = scopeRepo.findFirstByType(ScopeType.ROOT).orElseThrow();
        if (assignmentRepo.existsBySubjectIdAndRoleIdAndScopeIdAndRevokedAtIsNull(user.getId(), role.getId(), root.getId())) {
            return;
        }
        Assignment a = new Assignment();
        a.setSubject(user);
        a.setRole(role);
        a.setScope(root);
        assignmentRepo.save(a);
    }

    private void seedVerificationTemplate(String catSlug, UUID adminId) {
        UUID catId = categoryRepo.findBySlug(catSlug).orElseThrow().getId();
        if (templateService.current(catId, TemplateScope.VERIFICATION).isPresent()) {
            return;
        }
        templateService.saveNewVersion(catId, TemplateScope.VERIFICATION, List.of(
                new FieldDefinition("license_number", "License / registration number", FieldType.TEXT, true, List.of(),
                        "Your professional license or company registration number")
        ), adminId);
    }

    private void approveVerification(UUID userId, UUID orgId, String catSlug, UUID adminId) {
        UUID catId = categoryRepo.findBySlug(catSlug).orElseThrow().getId();
        ProviderVerificationResponse resp = pvService.submit(userId,
                new SubmitVerificationRequest(catId, orgId, Map.of("license_number", "LIC-" + userId.toString().substring(0, 8))));
        pvService.decide(adminId, resp.id(), true, null);
    }

    private void product(UUID ownerId, UUID orgId, String catSlug, String title, String desc, String price, ProductDetailDto product) {
        activate(ownerId, listingService.create(ownerId, new CreateListingRequest(
                title, desc, categoryId(catSlug), ListingType.PRODUCT, new BigDecimal(price), PriceUnit.PER_DAY,
                CITY, null, BigDecimal.ZERO, null, null, orgId, product, null)));
    }

    private void service(UUID ownerId, UUID orgId, String catSlug, String title, String desc, String price,
                         PriceUnit unit, ServiceDetailDto service) {
        activate(ownerId, listingService.create(ownerId, new CreateListingRequest(
                title, desc, categoryId(catSlug), ListingType.SERVICE, new BigDecimal(price), unit,
                CITY, null, BigDecimal.ZERO, null, null, orgId, null, service)));
    }

    private void activate(UUID ownerId, ListingResponse listing) {
        listingService.update(ownerId, listing.id(), new UpdateListingRequest(
                null, null, null, null, null, null, null, null, ListingStatus.ACTIVE, null, null));
    }

    private UUID categoryId(String slug) {
        return categoryRepo.findBySlug(slug).orElseThrow().getId();
    }
}
