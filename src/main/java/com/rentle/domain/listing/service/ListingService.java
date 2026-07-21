package com.rentle.domain.listing.service;

import com.rentle.domain.platform.catalog.PermissionKeys;
import com.rentle.shared.security.SecurityUtils;
import com.rentle.domain.listing.dto.CreateListingRequest;
import com.rentle.domain.listing.dto.ListingProviderDto;
import com.rentle.domain.listing.dto.ListingResponse;
import com.rentle.domain.listing.dto.ListingSummaryResponse;
import com.rentle.domain.organization.model.Organization;
import com.rentle.domain.organization.repository.OrganizationRepository;
import com.rentle.domain.organization.service.OrganizationService;
import com.rentle.domain.listing.dto.ProductDetailDto;
import com.rentle.domain.listing.dto.ServiceDetailDto;
import com.rentle.domain.listing.dto.UpdateListingRequest;
import com.rentle.domain.listing.model.Category;
import com.rentle.domain.listing.model.CategoryType;
import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.ListingStatus;
import com.rentle.domain.listing.model.ListingType;
import com.rentle.domain.listing.model.ProductDetail;
import com.rentle.domain.listing.model.ServiceDetail;
import com.rentle.domain.listing.repository.CategoryRepository;
import com.rentle.domain.listing.repository.ListingImageRepository;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.domain.listing.repository.ProductDetailRepository;
import com.rentle.domain.listing.repository.ServiceDetailRepository;
import com.rentle.domain.user.model.User;
import com.rentle.domain.user.model.UserStatus;
import com.rentle.domain.user.repository.UserRepository;
import com.rentle.shared.api.PageResponse;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.exception.UnauthorizedException;
import com.rentle.shared.security.RateLimitService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ListingService {

    private static final int LISTING_CREATE_PER_DAY = 10;

    private final ListingRepository listingRepository;
    private final CategoryRepository categoryRepository;
    private final ProductDetailRepository productDetailRepository;
    private final ServiceDetailRepository serviceDetailRepository;
    private final ListingImageRepository listingImageRepository;
    private final UserRepository userRepository;
    private final RateLimitService rateLimitService;
    private final com.rentle.domain.template.service.FieldTemplateService templateService;
    private final com.rentle.domain.verification.service.ProviderVerificationService providerVerification;
    private final OrganizationService organizationService;
    private final OrganizationRepository organizationRepository;

    public ListingService(ListingRepository listingRepository,
                          CategoryRepository categoryRepository,
                          ProductDetailRepository productDetailRepository,
                          ServiceDetailRepository serviceDetailRepository,
                          ListingImageRepository listingImageRepository,
                          UserRepository userRepository,
                          RateLimitService rateLimitService,
                          com.rentle.domain.template.service.FieldTemplateService templateService,
                          com.rentle.domain.verification.service.ProviderVerificationService providerVerification,
                          OrganizationService organizationService,
                          OrganizationRepository organizationRepository) {
        this.listingRepository = listingRepository;
        this.categoryRepository = categoryRepository;
        this.productDetailRepository = productDetailRepository;
        this.serviceDetailRepository = serviceDetailRepository;
        this.listingImageRepository = listingImageRepository;
        this.userRepository = userRepository;
        this.rateLimitService = rateLimitService;
        this.templateService = templateService;
        this.providerVerification = providerVerification;
        this.organizationService = organizationService;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public ListingResponse create(UUID ownerId, CreateListingRequest req) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (owner.getStatus() != UserStatus.VERIFIED) {
            throw new UnauthorizedException("Only verified users can create listings");
        }
        if (!rateLimitService.allow("listing-create:" + ownerId, LISTING_CREATE_PER_DAY, Duration.ofDays(1))) {
            throw new RentleException("Daily listing creation limit reached");
        }

        // When creating as an organization, the acting user must be a member allowed to list for it.
        Organization org = null;
        if (req.orgId() != null) {
            if (!organizationService.hasOrgPermission(ownerId, req.orgId(),
                    PermissionKeys.ORGANIZATION_LISTING_MANAGE)) {
                throw new UnauthorizedException("You cannot create listings for this organization");
            }
            org = organizationService.requireOrg(req.orgId());
        }

        Category category = categoryRepository.findById(req.categoryId())
                .filter(Category::getIsActive)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (category.getListingType() != CategoryType.BOTH
                && !category.getListingType().name().equals(req.type().name())) {
            throw new RentleException("Category does not accept " + req.type() + " listings");
        }

        if (req.type() == ListingType.PRODUCT && req.product() == null) {
            throw new RentleException("Product details are required for product listings");
        }
        if (req.type() == ListingType.SERVICE && req.service() == null) {
            throw new RentleException("Service details are required for service listings");
        }

        // Provider-verification gate (docs/07 Phase A): a SERVICE listing in a category that
        // requires credentials needs an approved provider verification for that category.
        boolean verified = org != null
                ? providerVerification.isVerifiedForOrg(org.getId(), category.getId())
                : providerVerification.isVerifiedFor(ownerId, category.getId());
        if (req.type() == ListingType.SERVICE && !verified) {
            throw new RentleException(
                    "This category requires provider verification. Submit your credentials for approval before listing.");
        }

        Listing listing = new Listing();
        listing.setOwner(owner);
        if (org != null) listing.setOrgId(org.getId());
        listing.setCategory(category);
        listing.setType(req.type());
        listing.setTitle(req.title());
        listing.setDescription(req.description());
        listing.setPricePerUnit(req.pricePerUnit());
        listing.setPriceUnit(req.priceUnit());
        listing.setDistrict(req.district());
        listing.setLocationText(req.locationText());
        listing.setRentalTerms(req.rentalTerms());
        listing.setDepositAmount(req.depositAmount() != null ? req.depositAmount() : BigDecimal.ZERO);
        // Validate + store this category's LISTING template answers, if a template is defined.
        java.util.Map<String, Object> attrs = req.attributes() != null ? req.attributes() : new java.util.HashMap<>();
        var listingTpl = templateService.current(category.getId(), com.rentle.domain.template.model.TemplateScope.LISTING);
        listingTpl.ifPresent(tpl -> templateService.validateAnswers(tpl.getFields(), attrs));
        listing.setAttributes(attrs);
        listing.setAttributesTemplateVersion(listingTpl.map(t -> t.getVersion()).orElse(null));
        listing = listingRepository.save(listing);

        ProductDetailDto productDto = null;
        ServiceDetailDto serviceDto = null;
        if (req.type() == ListingType.PRODUCT) {
            ProductDetail d = new ProductDetail();
            d.setListing(listing);
            d.setCondition(req.product().condition());
            d.setBrand(req.product().brand());
            d.setModel(req.product().model());
            if (req.product().minRentalDays() != null) d.setMinRentalDays(req.product().minRentalDays());
            d.setMaxRentalDays(req.product().maxRentalDays());
            productDto = ProductDetailDto.from(productDetailRepository.save(d));
        } else {
            ServiceDetail d = new ServiceDetail();
            d.setListing(listing);
            d.setServiceAreaKm(req.service().serviceAreaKm());
            d.setTypicalDuration(req.service().typicalDuration());
            if (req.service().minNoticeHours() != null) d.setMinNoticeHours(req.service().minNoticeHours());
            d.setPortfolioUrl(req.service().portfolioUrl());
            serviceDto = ServiceDetailDto.from(serviceDetailRepository.save(d));
        }

        return ListingResponse.from(listing, List.of(), productDto, serviceDto, providerFor(listing, org));
    }

    @Transactional
    public ListingResponse update(UUID ownerId, UUID listingId, UpdateListingRequest req) {
        Listing listing = getOwnedListing(ownerId, listingId);

        if (req.status() != null) {
            if (req.status() == ListingStatus.REMOVED || req.status() == ListingStatus.DRAFT) {
                throw new RentleException("Status can only be changed to ACTIVE or INACTIVE");
            }
            listing.setStatus(req.status());
        }
        if (req.title() != null) listing.setTitle(req.title());
        if (req.description() != null) listing.setDescription(req.description());
        if (req.pricePerUnit() != null) listing.setPricePerUnit(req.pricePerUnit());
        if (req.priceUnit() != null) listing.setPriceUnit(req.priceUnit());
        if (req.district() != null) listing.setDistrict(req.district());
        if (req.locationText() != null) listing.setLocationText(req.locationText());
        if (req.rentalTerms() != null) listing.setRentalTerms(req.rentalTerms());
        if (req.depositAmount() != null) listing.setDepositAmount(req.depositAmount());
        listing = listingRepository.save(listing);

        if (req.product() != null && listing.getType() == ListingType.PRODUCT) {
            ProductDetail d = productDetailRepository.findByListingId(listingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product detail not found"));
            d.setCondition(req.product().condition());
            d.setBrand(req.product().brand());
            d.setModel(req.product().model());
            if (req.product().minRentalDays() != null) d.setMinRentalDays(req.product().minRentalDays());
            d.setMaxRentalDays(req.product().maxRentalDays());
            productDetailRepository.save(d);
        }
        if (req.service() != null && listing.getType() == ListingType.SERVICE) {
            ServiceDetail d = serviceDetailRepository.findByListingId(listingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Service detail not found"));
            d.setServiceAreaKm(req.service().serviceAreaKm());
            d.setTypicalDuration(req.service().typicalDuration());
            if (req.service().minNoticeHours() != null) d.setMinNoticeHours(req.service().minNoticeHours());
            d.setPortfolioUrl(req.service().portfolioUrl());
            serviceDetailRepository.save(d);
        }

        return toResponse(listing);
    }

    @Transactional
    public void softDelete(UUID ownerId, UUID listingId) {
        Listing listing = getOwnedListing(ownerId, listingId);
        listing.setStatus(ListingStatus.REMOVED);
        listingRepository.save(listing);
    }

    @Transactional(readOnly = true)
    public ListingResponse getDetail(UUID listingId, UUID requesterId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        boolean isOwner = requesterId != null && listing.getOwner().getId().equals(requesterId);
        boolean canReadAny = SecurityUtils.hasAuthority(PermissionKeys.LISTING_LISTING_READ);
        if (listing.getStatus() == ListingStatus.REMOVED
                || (listing.getStatus() != ListingStatus.ACTIVE && !isOwner && !canReadAny)) {
            throw new ResourceNotFoundException("Listing not found");
        }
        return toResponse(listing);
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> publicListingsOf(UUID userId, Pageable pageable) {
        return toSummaryPage(listingRepository.findByOwnerIdAndStatus(userId, ListingStatus.ACTIVE, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> myListings(UUID ownerId, Pageable pageable) {
        return toSummaryPage(listingRepository.findByOwnerIdAndStatusNot(ownerId, ListingStatus.REMOVED, pageable));
    }

    /** Active-listing summaries for a set of ids (used by favorites), newest first. */
    @Transactional(readOnly = true)
    public List<ListingSummaryResponse> summariesByIds(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        List<Listing> listings = listingRepository.findAllById(ids).stream()
                .filter(l -> l.getStatus() == ListingStatus.ACTIVE)
                .sorted(java.util.Comparator.comparing(Listing::getCreatedAt).reversed())
                .toList();
        Map<UUID, String> covers = listingImageRepository.findByListingIdInOrderBySortOrderAsc(
                        listings.stream().map(Listing::getId).toList()).stream()
                .collect(Collectors.toMap(img -> img.getListing().getId(),
                        com.rentle.domain.listing.model.ListingImage::getUrl, (a, b) -> a));
        Map<UUID, Organization> orgs = orgsFor(listings);
        Map<UUID, User> owners = ownersFor(listings);
        return listings.stream().map(l -> ListingSummaryResponse.from(l, covers.get(l.getId()), providerOf(l, orgs, owners))).toList();
    }

    PageResponse<ListingSummaryResponse> toSummaryPage(Page<Listing> page) {
        List<UUID> ids = page.getContent().stream().map(Listing::getId).toList();
        Map<UUID, String> covers = ids.isEmpty() ? Map.of()
                : listingImageRepository.findByListingIdInOrderBySortOrderAsc(ids).stream()
                        .collect(Collectors.toMap(
                                img -> img.getListing().getId(),
                                com.rentle.domain.listing.model.ListingImage::getUrl,
                                (first, second) -> first));
        Map<UUID, Organization> orgs = orgsFor(page.getContent());
        Map<UUID, User> owners = ownersFor(page.getContent());
        return PageResponse.from(page, l -> ListingSummaryResponse.from(l, covers.get(l.getId()), providerOf(l, orgs, owners)));
    }

    /** Batch-load the organizations owning any of these listings, keyed by org id. */
    private Map<UUID, Organization> orgsFor(List<Listing> listings) {
        List<UUID> orgIds = listings.stream().map(Listing::getOrgId).filter(java.util.Objects::nonNull).distinct().toList();
        if (orgIds.isEmpty()) return Map.of();
        return organizationRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(Organization::getId, o -> o));
    }

    private Map<UUID, User> ownersFor(List<Listing> listings) {
        List<UUID> ownerIds = listings.stream().map(l -> l.getOwner().getId()).distinct().toList();
        if (ownerIds.isEmpty()) return Map.of();
        return userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    private ListingProviderDto providerOf(Listing l, Map<UUID, Organization> orgs, Map<UUID, User> owners) {
        if (l.getOrgId() == null) {
            User owner = owners.get(l.getOwner().getId());
            return owner != null ? ListingProviderDto.user(owner) : null;
        }
        Organization org = orgs.get(l.getOrgId());
        return org != null ? ListingProviderDto.org(org) : null;
    }

    private ListingResponse toResponse(Listing listing) {
        ProductDetailDto product = productDetailRepository.findByListingId(listing.getId())
                .map(ProductDetailDto::from).orElse(null);
        ServiceDetailDto service = serviceDetailRepository.findByListingId(listing.getId())
                .map(ServiceDetailDto::from).orElse(null);
        return ListingResponse.from(listing,
                listingImageRepository.findByListingIdOrderBySortOrderAsc(listing.getId()),
                product, service, providerFor(listing, null));
    }

    private Listing getOwnedListing(UUID ownerId, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        boolean isOwner = listing.getOwner().getId().equals(ownerId);
        boolean orgManager = listing.getOrgId() != null && organizationService.hasOrgPermission(
                ownerId, listing.getOrgId(), PermissionKeys.ORGANIZATION_LISTING_MANAGE);
        if (!isOwner && !orgManager) {
            throw new UnauthorizedException("Only the listing owner can modify this listing");
        }
        return listing;
    }

    /** Listings owned by an organization (any non-removed status) for its members' dashboard. */
    @Transactional(readOnly = true)
    public PageResponse<ListingSummaryResponse> orgListings(UUID userId, UUID orgId, Pageable pageable) {
        if (!organizationService.hasOrgPermission(userId, orgId, PermissionKeys.ORGANIZATION_LISTING_MANAGE)) {
            throw new UnauthorizedException("You are not a member of this organization");
        }
        return toSummaryPage(listingRepository.findByOrgIdAndStatusNot(orgId, ListingStatus.REMOVED, pageable));
    }

    /** Provider identity for a listing — the org when org-owned, otherwise the individual owner. */
    private ListingProviderDto providerFor(Listing listing, Organization org) {
        if (listing.getOrgId() == null) return ListingProviderDto.user(listing.getOwner());
        Organization resolved = org != null ? org
                : organizationRepository.findById(listing.getOrgId()).orElse(null);
        return resolved != null ? ListingProviderDto.org(resolved) : ListingProviderDto.user(listing.getOwner());
    }
}
