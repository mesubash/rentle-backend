package com.rentle.integration;

import com.rentle.config.TestcontainersConfig;
import com.rentle.domain.booking.dto.BookingResponse;
import com.rentle.domain.booking.dto.CreateBookingRequest;
import com.rentle.domain.booking.service.BookingService;
import com.rentle.domain.business.dto.WorkerRequest;
import com.rentle.domain.business.dto.WorkerResponse;
import com.rentle.domain.listing.dto.CreateListingRequest;
import com.rentle.domain.listing.dto.ListingResponse;
import com.rentle.domain.listing.dto.ProductDetailDto;
import com.rentle.domain.listing.model.Category;
import com.rentle.domain.listing.model.ItemCondition;
import com.rentle.domain.listing.model.ListingType;
import com.rentle.domain.listing.model.PriceUnit;
import com.rentle.domain.listing.repository.CategoryRepository;
import com.rentle.domain.listing.service.ListingService;
import com.rentle.domain.organization.dto.CreateOrgRequest;
import com.rentle.domain.organization.dto.InviteResponse;
import com.rentle.domain.organization.dto.OrgResponse;
import com.rentle.domain.organization.dto.OrgRoleResponse;
import com.rentle.domain.organization.service.OrganizationService;
import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.domain.platform.catalog.RoleSeeds;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestcontainersConfig.class)
class OrganizationFlowIntegrationTest {

    @Autowired OrganizationService organizationService;
    @Autowired ListingService listingService;
    @Autowired BookingService bookingService;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = createUser();
    }

    private User createUser() {
        long n = System.nanoTime() % 1_000_000_000L;
        User u = new User();
        u.setPhoneNumber("+97798" + n);
        u.setEmail("org" + n + "@test.com");
        u.setPasswordHash("$2a$12$test");
        u.setFullName("Org User " + n);
        u.setStatus(UserStatus.VERIFIED);
        u.setPhoneVerified(true);
        u.setEmailVerified(true);
        u.setCitizenshipVerified(true);
        return userRepository.save(u);
    }

    private OrgResponse createOrg(User creator) {
        return organizationService.create(creator.getId(), new CreateOrgRequest("Movers " + System.nanoTime(), "We move things", null));
    }

    private UUID orgRoleId(OrgResponse org, String roleName) {
        return organizationService.assignableRoles().stream()
                .filter(r -> r.name().equals(roleName)).map(OrgRoleResponse::id).findFirst().orElseThrow();
    }

    @Test
    void creatorBecomesOwnerWithAllOrgPermissions() {
        OrgResponse org = createOrg(owner);
        assertNotNull(org.id());
        assertTrue(org.myPermissions().contains(PermissionKeys.ORGANIZATION_ORG_MANAGE));
        assertTrue(org.myPermissions().contains(PermissionKeys.ORGANIZATION_MEMBER_MANAGE));
        assertTrue(organizationService.hasOrgPermission(owner.getId(), org.id(), PermissionKeys.ORGANIZATION_LISTING_MANAGE));
        // The creator's org appears in their switcher list.
        assertEquals(1, organizationService.myOrganizations(owner.getId()).size());
    }

    @Test
    void inviteAcceptGrantsScopedStaffPermissionsOnly() {
        OrgResponse org = createOrg(owner);
        User staff = createUser();
        UUID staffRole = orgRoleId(org, RoleSeeds.ORG_STAFF);

        InviteResponse invite = organizationService.invite(owner.getId(), org.id(),
                new com.rentle.domain.organization.dto.InviteRequest(staff.getEmail(), staffRole));
        assertNotNull(invite.token());

        // Non-member cannot read the org before accepting.
        assertThrows(UnauthorizedException.class, () -> organizationService.get(staff.getId(), org.id()));

        OrgResponse joined = organizationService.acceptInvite(staff.getId(), invite.token());
        // STAFF has operational permissions but NOT member/org management.
        assertTrue(joined.myPermissions().contains(PermissionKeys.ORGANIZATION_LISTING_MANAGE));
        assertTrue(joined.myPermissions().contains(PermissionKeys.ORGANIZATION_BOOKING_MANAGE));
        assertFalse(joined.myPermissions().contains(PermissionKeys.ORGANIZATION_MEMBER_MANAGE));

        // Staff cannot invite others.
        assertThrows(UnauthorizedException.class, () -> organizationService.invite(staff.getId(), org.id(),
                new com.rentle.domain.organization.dto.InviteRequest("x@test.com", staffRole)));

        assertEquals(2, organizationService.members(owner.getId(), org.id()).size());
    }

    @Test
    void wrongEmailCannotAcceptInvite() {
        OrgResponse org = createOrg(owner);
        User invited = createUser();
        User stranger = createUser();
        InviteResponse invite = organizationService.invite(owner.getId(), org.id(),
                new com.rentle.domain.organization.dto.InviteRequest(invited.getEmail(), orgRoleId(org, RoleSeeds.ORG_STAFF)));
        assertThrows(UnauthorizedException.class, () -> organizationService.acceptInvite(stranger.getId(), invite.token()));
    }

    @Test
    void lastOwnerCannotBeRemoved() {
        OrgResponse org = createOrg(owner);
        assertThrows(RentleException.class, () -> organizationService.removeMember(owner.getId(), org.id(), owner.getId()));
    }

    @Test
    void listingCreatedAsOrgIsOrgOwnedAndBlocksNonMembers() {
        OrgResponse org = createOrg(owner);
        Category category = categoryRepository.findBySlug("cameras-tech").orElseThrow();

        ListingResponse listing = listingService.create(owner.getId(), orgListingRequest(category.getId(), org.id()));
        assertEquals("ORG", listing.provider().type());
        assertEquals(org.id(), listing.provider().id());

        // A non-member cannot create a listing for the org.
        User outsider = createUser();
        assertThrows(UnauthorizedException.class,
                () -> listingService.create(outsider.getId(), orgListingRequest(category.getId(), org.id())));
    }

    @Test
    void orgListingBookingIsProviderScopedAndWorkerAssignable() {
        OrgResponse org = createOrg(owner);
        Category category = categoryRepository.findBySlug("cameras-tech").orElseThrow();
        ListingResponse listing = listingService.create(owner.getId(), orgListingRequest(category.getId(), org.id()));
        // Activate the listing so it can be booked (status is the 9th field).
        listingService.update(owner.getId(), listing.id(),
                new com.rentle.domain.listing.dto.UpdateListingRequest(
                        null, null, null, null, null, null, null, null,
                        com.rentle.domain.listing.model.ListingStatus.ACTIVE, null, null));

        WorkerResponse worker = organizationService.addWorker(owner.getId(), org.id(),
                new WorkerRequest("Ram", "9800000000", "Driver"));

        User renter = createUser();
        BookingResponse booking = bookingService.createBooking(renter.getId(),
                new CreateBookingRequest(listing.id(), LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), null, null, "hi", null));

        // Org (via the member) can see and act on the incoming booking, and assign an org worker.
        assertEquals(1, bookingService.orgBookings(owner.getId(), org.id(), PageRequest.of(0, 10)).content().size());
        BookingResponse assigned = bookingService.assignWorker(owner.getId(), booking.id(), worker.id());
        assertEquals("Ram", assigned.assignedWorkerName());
    }

    @Test
    void adminListingViewSeesOrgAndMembers() {
        OrgResponse org = createOrg(owner);
        var rows = organizationService.adminList(null, PageRequest.of(0, 50)).content();
        assertTrue(rows.stream().anyMatch(r -> r.id().equals(org.id())));
        var detail = organizationService.adminGet(org.id());
        assertEquals(1, detail.members().size());
        assertEquals(owner.getEmail(), detail.members().get(0).email());
    }

    private CreateListingRequest orgListingRequest(UUID categoryId, UUID orgId) {
        return new CreateListingRequest(
                "Org Camera Kit " + (System.nanoTime() % 10000),
                "A great camera kit owned by the organization for rent.",
                categoryId, ListingType.PRODUCT, new BigDecimal("1000.00"), PriceUnit.PER_DAY,
                "Kathmandu", null, BigDecimal.ZERO, null, null, orgId,
                new ProductDetailDto(ItemCondition.GOOD, null, null, 1, null), null);
    }
}
