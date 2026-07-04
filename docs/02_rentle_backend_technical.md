# Rentle — Backend Technical Implementation Guide
**Version:** 1.0  
**Stack:** Java 21 · Spring Boot 3.2+ · PostgreSQL 16 · Redis · Docker  
**Phase:** 1 (Phase 1 scope only)  
**Audience:** Backend developers

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Tech Stack Decisions](#2-tech-stack-decisions)
3. [Project Structure](#3-project-structure)
4. [Domain Module Design](#4-domain-module-design)
5. [Core Entities & DTOs](#5-core-entities--dtos)
6. [Business Logic — Booking State Machine](#6-business-logic--booking-state-machine)
7. [API Design](#7-api-design)
8. [Security Architecture](#8-security-architecture)
9. [Database Strategy](#9-database-strategy)
10. [File Upload Strategy](#10-file-upload-strategy)
11. [Notification Strategy](#11-notification-strategy)
12. [Error Handling](#12-error-handling)
13. [Testing Strategy](#13-testing-strategy)
14. [Infrastructure & Deployment](#14-infrastructure--deployment)
15. [Development Environment Setup](#15-development-environment-setup)
16. [Phase 2 Readiness Checklist](#16-phase-2-readiness-checklist)

---

## 1. Architecture Overview

### 1.1 Pattern: Modular Monolith

Rentle Phase 1 is a **modular monolith** — a single deployable unit internally structured like a future microservices system. Each domain (user, listing, booking, review, messaging) is a self-contained module with its own models, repositories, services, and controllers. Modules never call each other's services directly — they communicate via **domain events** published through a shared event bus.

This gives us microservices-level maintainability without the distributed systems complexity. When Phase 3 demands extraction of the payment or notification domain, the boundary is already clean.

```
HTTP Request
     │
     ▼
┌─────────────────────────┐
│  Spring Security Filter  │  ← JWT validation, rate limiting
└────────────┬────────────┘
             │
     ┌───────▼────────┐
     │   Controllers   │  ← Input validation (@Valid), response mapping
     └───────┬────────┘
             │
     ┌───────▼────────┐
     │    Services     │  ← Business logic, state transitions, event publishing
     └───────┬────────┘
             │
     ┌───────▼────────┐
     │  Repositories   │  ← JPA, custom JPQL queries, DB-level constraints
     └───────┬────────┘
             │
     ┌───────▼────────┐
     │  PostgreSQL 16  │  ← Triggers, constraints, GiST indexes
     └─────────────────┘
```

### 1.2 The Listing Abstraction

The single most important architectural decision is that **everything is a Listing**. A `Listing` has a `type` field (`PRODUCT` or `SERVICE`). Type-specific data lives in separate 1:1 satellite tables (`product_detail`, `service_detail`). This means:

- Booking, Review, Message, Payment (future) — all reference `listing_id`, never `product_id` or `service_id`
- Adding a new listing type in future requires only a new detail table, not a schema overhaul
- Search, filtering, and category browsing work identically for products and services

### 1.3 Cross-Module Communication

Modules publish domain events. Other modules listen. No module imports another module's service.

```java
// booking module publishes
eventPublisher.publish(new BookingCompletedEvent(booking.getId()));

// review module listens
@EventListener
public void onBookingCompleted(BookingCompletedEvent event) {
    reviewEligibilityService.openReviewWindow(event.getBookingId());
}
```

---

## 2. Tech Stack Decisions

### 2.1 Core

| Component | Choice | Reason |
|-----------|--------|--------|
| Language | Java 21 LTS | Records, virtual threads (Project Loom), strong typing, Nepal dev pool |
| Framework | Spring Boot 3.2+ | Mature, well-known, auto-configuration |
| Security | Spring Security 6 | JWT, method-level auth, CSRF |
| ORM | JPA / Hibernate 6 | Lazy loading, caching, audit support |
| DB Migration | Flyway 10 | Version-controlled, repeatable schema |
| Validation | Hibernate Validator (JSR-380) | Annotation-based, works with @Valid |
| Build | Maven 3.9+ | Standard, CI-friendly |

### 2.2 Data

| Component | Choice | Reason |
|-----------|--------|--------|
| Primary DB | PostgreSQL 16 | ACID, GiST indexes for overlap queries, JSONB, full-text search |
| Cache | Redis 7 | JWT blacklist, OTP storage, session, rate limiting |
| Search | PostgreSQL FTS | Sufficient for Phase 1. Elasticsearch deferred to Phase 2. |
| Migrations | Flyway | One source of truth for schema |

### 2.3 Infrastructure

| Component | Choice | Reason |
|-----------|--------|--------|
| File storage | Cloudinary | Free tier, transform API, Nepal-accessible CDN |
| SMS | Sparrow SMS (Nepal) | Local provider, reliable for OTP, Nepali numbers |
| Email | SendGrid | Free tier covers Phase 1 volume |
| Containerisation | Docker + Docker Compose | Reproducible dev env |
| Hosting (Phase 1) | DigitalOcean Droplet or AWS EC2 t3.small | Cost-effective for Phase 1 load |

### 2.4 What We Are NOT Using in Phase 1

| Skipped | Why |
|---------|-----|
| RabbitMQ | Overkill under 1000 users. Spring `ApplicationEventPublisher` (in-process events) is sufficient. Add in Phase 2. |
| Elasticsearch | PostgreSQL FTS handles Phase 1 search. |
| Kubernetes | Single server deployment is fine for Phase 1. |
| WebSockets | Message polling every 30s is acceptable for Phase 1 volume. |

---

## 3. Project Structure

```
rentle-backend/
│
├── pom.xml
├── docker-compose.yml
├── README.md
│
└── src/
    ├── main/
    │   ├── java/com/rentle/
    │   │   │
    │   │   ├── RentleApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── JwtConfig.java
    │   │   │   ├── RedisConfig.java
    │   │   │   ├── CloudinaryConfig.java
    │   │   │   └── OpenAPIConfig.java
    │   │   │
    │   │   ├── domain/
    │   │   │   │
    │   │   │   ├── user/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── User.java
    │   │   │   │   │   ├── UserRole.java          (enum: USER, ADMIN)
    │   │   │   │   │   └── UserStatus.java        (enum: PENDING, VERIFIED, SUSPENDED)
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── RegisterRequest.java
    │   │   │   │   │   ├── LoginRequest.java
    │   │   │   │   │   ├── OtpVerifyRequest.java
    │   │   │   │   │   ├── UserProfileResponse.java
    │   │   │   │   │   └── UpdateProfileRequest.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── UserRepository.java
    │   │   │   │   ├── service/
    │   │   │   │   │   ├── UserService.java
    │   │   │   │   │   ├── AuthService.java
    │   │   │   │   │   └── OtpService.java
    │   │   │   │   └── controller/
    │   │   │   │       ├── AuthController.java
    │   │   │   │       └── UserController.java
    │   │   │   │
    │   │   │   ├── listing/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── Listing.java
    │   │   │   │   │   ├── ListingType.java       (enum: PRODUCT, SERVICE)
    │   │   │   │   │   ├── ListingStatus.java     (enum: DRAFT, ACTIVE, INACTIVE, REMOVED)
    │   │   │   │   │   ├── ProductDetail.java
    │   │   │   │   │   ├── ServiceDetail.java
    │   │   │   │   │   ├── ListingImage.java
    │   │   │   │   │   ├── Category.java
    │   │   │   │   │   └── UnavailableRange.java
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── CreateListingRequest.java
    │   │   │   │   │   ├── UpdateListingRequest.java
    │   │   │   │   │   ├── ListingResponse.java
    │   │   │   │   │   ├── ListingSearchRequest.java
    │   │   │   │   │   └── AvailabilityResponse.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   ├── ListingRepository.java
    │   │   │   │   │   ├── CategoryRepository.java
    │   │   │   │   │   └── UnavailableRangeRepository.java
    │   │   │   │   ├── service/
    │   │   │   │   │   ├── ListingService.java
    │   │   │   │   │   ├── ListingSearchService.java
    │   │   │   │   │   ├── AvailabilityService.java
    │   │   │   │   │   └── ListingImageService.java
    │   │   │   │   └── controller/
    │   │   │   │       ├── ListingController.java
    │   │   │   │       └── CategoryController.java
    │   │   │   │
    │   │   │   ├── booking/
    │   │   │   │   ├── model/
    │   │   │   │   │   ├── Booking.java
    │   │   │   │   │   └── BookingStatus.java
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── CreateBookingRequest.java
    │   │   │   │   │   ├── BookingResponse.java
    │   │   │   │   │   └── BookingActionRequest.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── BookingRepository.java
    │   │   │   │   ├── service/
    │   │   │   │   │   ├── BookingService.java
    │   │   │   │   │   ├── BookingStateMachine.java
    │   │   │   │   │   └── PricingService.java
    │   │   │   │   └── controller/
    │   │   │   │       └── BookingController.java
    │   │   │   │
    │   │   │   ├── review/
    │   │   │   │   ├── model/
    │   │   │   │   │   └── Review.java
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── CreateReviewRequest.java
    │   │   │   │   │   └── ReviewResponse.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── ReviewRepository.java
    │   │   │   │   ├── service/
    │   │   │   │   │   └── ReviewService.java
    │   │   │   │   └── controller/
    │   │   │   │       └── ReviewController.java
    │   │   │   │
    │   │   │   ├── messaging/
    │   │   │   │   ├── model/
    │   │   │   │   │   └── Message.java
    │   │   │   │   ├── dto/
    │   │   │   │   │   ├── SendMessageRequest.java
    │   │   │   │   │   └── MessageResponse.java
    │   │   │   │   ├── repository/
    │   │   │   │   │   └── MessageRepository.java
    │   │   │   │   ├── service/
    │   │   │   │   │   └── MessageService.java
    │   │   │   │   └── controller/
    │   │   │   │       └── MessageController.java
    │   │   │   │
    │   │   │   └── admin/
    │   │   │       ├── dto/
    │   │   │       │   └── AdminUserResponse.java
    │   │   │       ├── service/
    │   │   │       │   └── AdminService.java
    │   │   │       └── controller/
    │   │   │           └── AdminController.java
    │   │   │
    │   │   └── shared/
    │   │       ├── entity/
    │   │       │   └── BaseEntity.java            (id, createdAt, updatedAt)
    │   │       ├── security/
    │   │       │   ├── JwtTokenProvider.java
    │   │       │   ├── JwtAuthFilter.java
    │   │       │   └── SecurityUtils.java
    │   │       ├── event/
    │   │       │   ├── BookingCreatedEvent.java
    │   │       │   ├── BookingApprovedEvent.java
    │   │       │   ├── BookingCompletedEvent.java
    │   │       │   └── ReviewCreatedEvent.java
    │   │       ├── exception/
    │   │       │   ├── GlobalExceptionHandler.java
    │   │       │   ├── RentleException.java
    │   │       │   ├── ResourceNotFoundException.java
    │   │       │   ├── UnauthorizedException.java
    │   │       │   ├── BookingConflictException.java
    │   │       │   ├── InvalidStateTransitionException.java
    │   │       │   └── ErrorResponse.java
    │   │       ├── notification/
    │   │       │   ├── SmsService.java            (interface)
    │   │       │   ├── SparrowSmsService.java     (impl)
    │   │       │   └── EmailService.java
    │   │       └── util/
    │   │           ├── SlugUtils.java
    │   │           └── DateUtils.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/
    │           ├── V001__initial_schema.sql
    │           ├── V002__constraints_and_indexes.sql
    │           ├── V003__booking_triggers.sql
    │           └── V004__seed_categories.sql
    │
    └── test/
        └── java/com/rentle/
            ├── integration/
            │   ├── BookingFlowIntegrationTest.java
            │   └── AuthIntegrationTest.java
            ├── unit/
            │   ├── BookingStateMachineTest.java
            │   ├── PricingServiceTest.java
            │   └── AvailabilityServiceTest.java
            └── config/
                └── TestContainersConfig.java
```

---

## 4. Domain Module Design

### 4.1 Rule: No Cross-Domain Service Calls

Modules communicate exclusively through Spring `ApplicationEventPublisher`. This is the single most important rule for keeping the codebase maintainable.

```
✅ booking module publishes BookingCompletedEvent
✅ review module listens to BookingCompletedEvent
❌ booking module injects ReviewService
```

### 4.2 Shared Kernel vs Domain

The `shared` package contains only:
- `BaseEntity` — id, createdAt, updatedAt (all entities extend this)
- Security utilities — JWT provider, auth filter, SecurityUtils
- Domain events — plain records, no business logic
- Exception classes — typed exceptions, global handler
- Notification services — SMS and email
- Common utilities

The `shared` package must never import from any `domain.*` package.

### 4.3 BaseEntity

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
```

Use `UUID` as the primary key type for all entities. This prevents ID enumeration attacks and is required for future horizontal scaling.

---

## 5. Core Entities & DTOs

### 5.1 User

```java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_phone", columnList = "phone_number", unique = true),
    @Index(name = "idx_users_email", columnList = "email", unique = true)
})
public class User extends BaseEntity {

    @Column(unique = true, nullable = false, length = 20)
    private String phoneNumber;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    private Boolean phoneVerified = false;
    private String citizenshipCardUrl;       // Cloudinary URL
    private Boolean citizenshipVerified = false;

    @Column(precision = 3, scale = 2)
    private BigDecimal trustScore;            // Calculated average rating

    private Integer failedLoginAttempts = 0;
    private Instant lockedUntil;
    private Instant lastLoginAt;
}
```

### 5.2 Listing

```java
@Entity
@Table(name = "listings", indexes = {
    @Index(name = "idx_listings_owner", columnList = "owner_id"),
    @Index(name = "idx_listings_category", columnList = "category_id"),
    @Index(name = "idx_listings_type_status", columnList = "type, status"),
    @Index(name = "idx_listings_district", columnList = "district")
})
public class Listing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ListingType type;                // PRODUCT | SERVICE

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ListingStatus status = ListingStatus.DRAFT;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PriceUnit priceUnit;             // PER_DAY | PER_HOUR | FLAT

    @Column(nullable = false, length = 50)
    private String district;                 // Kathmandu, Lalitpur, Pokhara...

    @Column(length = 200)
    private String locationText;             // "Near Thamel, Kathmandu"

    private BigDecimal depositAmount = BigDecimal.ZERO;

    @Column(precision = 3, scale = 2)
    private BigDecimal averageRating;
    private Integer reviewCount = 0;
    private Integer totalBookings = 0;

    // Full-text search vector (maintained by DB trigger)
    // Not mapped — used directly in native queries
}
```

### 5.3 ProductDetail & ServiceDetail (1:1 with Listing)

```java
@Entity
@Table(name = "product_details")
public class ProductDetail extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", unique = true, nullable = false)
    private Listing listing;

    @Enumerated(EnumType.STRING)
    private ItemCondition condition;         // NEW | GOOD | FAIR

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String model;

    private Integer minRentalDays = 1;
    private Integer maxRentalDays;
}

@Entity
@Table(name = "service_details")
public class ServiceDetail extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", unique = true, nullable = false)
    private Listing listing;

    private Integer serviceAreaKm;           // max radius provider covers

    @Enumerated(EnumType.STRING)
    private ServiceDuration typicalDuration; // HOURLY | HALF_DAY | FULL_DAY | CUSTOM

    private Integer minNoticeHours = 24;    // minimum booking advance notice

    @Column(length = 500)
    private String portfolioUrl;
}
```

### 5.4 Booking

```java
@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_listing", columnList = "listing_id"),
    @Index(name = "idx_bookings_renter", columnList = "renter_id"),
    @Index(name = "idx_bookings_status", columnList = "status"),
    @Index(name = "idx_bookings_dates", columnList = "listing_id, start_date, end_date")
})
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renter_id", nullable = false)
    private User renter;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    // For service bookings — specific time slot
    private LocalTime startTime;
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.REQUESTED;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal depositAmount;
    private Boolean depositPaid = false;
    private String depositProofUrl;          // Screenshot uploaded by renter

    @Column(length = 500)
    private String renterNote;

    @Column(length = 500)
    private String cancellationReason;
    private Instant cancelledAt;
    private UUID cancelledBy;
}
```

### 5.5 BookingStatus Enum & Transitions

```java
public enum BookingStatus {
    REQUESTED,
    APPROVED,
    DEPOSIT_PENDING,    // After approval, waiting for deposit confirmation
    ACTIVE,             // Deposit confirmed, rental in progress
    COMPLETED,
    CANCELLED,
    REJECTED
}

// Valid transitions:
// REQUESTED     → APPROVED, REJECTED, CANCELLED
// APPROVED      → DEPOSIT_PENDING, CANCELLED
// DEPOSIT_PENDING → ACTIVE, CANCELLED
// ACTIVE        → COMPLETED, CANCELLED
// COMPLETED     → (terminal)
// CANCELLED     → (terminal)
// REJECTED      → (terminal)
```

### 5.6 Review

```java
@Entity
@Table(name = "reviews",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_review_booking_author",
        columnNames = {"booking_id", "author_id"}
    )
)
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private User subject;                   // who is being reviewed

    @Column(nullable = false)
    private Integer rating;                 // 1–5 (CHECK constraint in DB)

    @Column(length = 500)
    private String comment;

    // DB trigger enforces: createdAt <= booking.completedAt + 30 days
}
```

### 5.7 Message

```java
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_messages_booking", columnList = "booking_id, created_at")
})
public class Message extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private Boolean isRead = false;
    private Instant readAt;
}
```

---

## 6. Business Logic — Booking State Machine

The state machine is enforced at two levels:
1. **Java service layer** — validates transitions before persisting
2. **PostgreSQL trigger** — rejects invalid transitions at the DB level (last line of defence)

### 6.1 BookingStateMachine

```java
@Component
public class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> VALID_TRANSITIONS =
        Map.of(
            REQUESTED,       Set.of(APPROVED, REJECTED, CANCELLED),
            APPROVED,        Set.of(DEPOSIT_PENDING, CANCELLED),
            DEPOSIT_PENDING, Set.of(ACTIVE, CANCELLED),
            ACTIVE,          Set.of(COMPLETED, CANCELLED),
            COMPLETED,       Set.of(),
            CANCELLED,       Set.of(),
            REJECTED,        Set.of()
        );

    public void assertValidTransition(BookingStatus from, BookingStatus to) {
        if (!VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidStateTransitionException(
                "Cannot transition booking from %s to %s".formatted(from, to)
            );
        }
    }
}
```

### 6.2 Booking Service — Core Operations

```java
@Service
@Transactional
public class BookingService {

    public BookingResponse createBooking(UUID requesterId, CreateBookingRequest req) {
        Listing listing = listingRepository.findByIdAndStatus(req.listingId(), ACTIVE)
            .orElseThrow(() -> new ResourceNotFoundException("Listing not found or inactive"));

        // Self-booking guard (also enforced by DB trigger)
        if (listing.getOwner().getId().equals(requesterId)) {
            throw new RentleException("You cannot book your own listing");
        }

        // Availability check (also enforced by DB constraint)
        availabilityService.assertAvailable(req.listingId(), req.startDate(), req.endDate());

        BigDecimal price = pricingService.calculate(listing, req.startDate(), req.endDate(), req.startTime(), req.endTime());

        Booking booking = new Booking();
        booking.setListing(listing);
        booking.setRenter(userRepository.getReferenceById(requesterId));
        booking.setStartDate(req.startDate());
        booking.setEndDate(req.endDate());
        booking.setStartTime(req.startTime());
        booking.setEndTime(req.endTime());
        booking.setTotalPrice(price);
        booking.setDepositAmount(listing.getDepositAmount());
        booking.setRenterNote(req.note());

        booking = bookingRepository.save(booking);
        eventPublisher.publishEvent(new BookingCreatedEvent(booking.getId(), listing.getOwner().getId()));
        return bookingMapper.toResponse(booking);
    }

    public BookingResponse approve(UUID ownerId, UUID bookingId) {
        Booking booking = getBookingForOwner(bookingId, ownerId);
        stateMachine.assertValidTransition(booking.getStatus(), APPROVED);
        booking.setStatus(APPROVED);
        eventPublisher.publishEvent(new BookingApprovedEvent(booking.getId()));
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    public BookingResponse confirmDeposit(UUID ownerId, UUID bookingId) {
        Booking booking = getBookingForOwner(bookingId, ownerId);
        stateMachine.assertValidTransition(booking.getStatus(), ACTIVE);
        booking.setStatus(ACTIVE);
        booking.setDepositPaid(true);
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }

    public BookingResponse complete(UUID actorId, UUID bookingId) {
        Booking booking = getBookingForParticipant(bookingId, actorId);
        stateMachine.assertValidTransition(booking.getStatus(), COMPLETED);
        booking.setStatus(COMPLETED);
        eventPublisher.publishEvent(new BookingCompletedEvent(booking.getId()));
        return bookingMapper.toResponse(bookingRepository.save(booking));
    }
}
```

### 6.3 Pricing Service

```java
@Service
public class PricingService {

    public BigDecimal calculate(Listing listing, LocalDate start, LocalDate end,
                                LocalTime startTime, LocalTime endTime) {
        return switch (listing.getPriceUnit()) {
            case PER_DAY  -> {
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                yield listing.getPricePerUnit().multiply(BigDecimal.valueOf(days));
            }
            case PER_HOUR -> {
                if (startTime == null || endTime == null) throw new RentleException("Time required for hourly listings");
                long hours = ChronoUnit.HOURS.between(startTime, endTime);
                yield listing.getPricePerUnit().multiply(BigDecimal.valueOf(hours));
            }
            case FLAT     -> listing.getPricePerUnit();
        };
    }
}
```

---

## 7. API Design

### 7.1 URL Conventions

- Base path: `/api/v1`
- Resources are plural nouns
- Actions on a resource use sub-paths, not verbs in the URL
- All responses use standard envelope: `{ data, error, timestamp }`

### 7.2 Complete Endpoint Map

#### Auth
```
POST   /api/v1/auth/register          Register with phone + email + password
POST   /api/v1/auth/login             Login, receive access + refresh tokens
POST   /api/v1/auth/refresh           Refresh access token
POST   /api/v1/auth/logout            Invalidate refresh token (Redis blacklist)
POST   /api/v1/auth/otp/send          Send OTP to phone
POST   /api/v1/auth/otp/verify        Verify OTP, set phoneVerified = true
```

#### Users
```
GET    /api/v1/users/me               Current user's full profile
PUT    /api/v1/users/me               Update name, email, profile photo
POST   /api/v1/users/me/citizenship   Upload citizenship card
GET    /api/v1/users/{id}             Public profile (name, rating, listings)
GET    /api/v1/users/{id}/listings    User's active listings
GET    /api/v1/users/{id}/reviews     Reviews about this user
```

#### Categories
```
GET    /api/v1/categories             All categories (flat list)
GET    /api/v1/categories/tree        Hierarchical tree
```

#### Listings
```
POST   /api/v1/listings               Create listing (auth required, VERIFIED only)
GET    /api/v1/listings               Search/browse listings
GET    /api/v1/listings/{id}          Listing detail
PUT    /api/v1/listings/{id}          Update listing (owner only)
DELETE /api/v1/listings/{id}          Soft-delete (owner only)
POST   /api/v1/listings/{id}/images   Upload image(s)
DELETE /api/v1/listings/{id}/images/{imageId}
GET    /api/v1/listings/{id}/availability  Unavailable date ranges
POST   /api/v1/listings/{id}/availability  Block dates (owner)
DELETE /api/v1/listings/{id}/availability/{rangeId}
```

#### Bookings
```
POST   /api/v1/bookings               Create booking request
GET    /api/v1/bookings/me/as-renter  My bookings as renter
GET    /api/v1/bookings/me/as-owner   My bookings as owner
GET    /api/v1/bookings/{id}          Booking detail (participants only)
POST   /api/v1/bookings/{id}/approve  Owner approves
POST   /api/v1/bookings/{id}/reject   Owner rejects
POST   /api/v1/bookings/{id}/deposit  Renter uploads deposit proof
POST   /api/v1/bookings/{id}/confirm-deposit  Owner confirms deposit
POST   /api/v1/bookings/{id}/complete Mark complete
POST   /api/v1/bookings/{id}/cancel   Cancel (with reason)
```

#### Messages
```
GET    /api/v1/bookings/{id}/messages  Get all messages for booking
POST   /api/v1/bookings/{id}/messages  Send message
PUT    /api/v1/bookings/{id}/messages/read  Mark all as read
```

#### Reviews
```
POST   /api/v1/reviews                Create review (COMPLETED booking required)
GET    /api/v1/listings/{id}/reviews  All reviews for a listing
GET    /api/v1/users/{id}/reviews     All reviews about a user
```

#### Admin
```
GET    /api/v1/admin/users            List all users (paginated)
PUT    /api/v1/admin/users/{id}/verify    Approve citizenship verification
PUT    /api/v1/admin/users/{id}/suspend   Suspend user
GET    /api/v1/admin/bookings         All bookings (paginated)
GET    /api/v1/admin/listings         All listings (paginated)
```

### 7.3 Standard Response Envelope

```java
public record ApiResponse<T>(
    T data,
    String error,
    Instant timestamp
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(null, message, Instant.now());
    }
}
```

### 7.4 Pagination

All list endpoints return:
```json
{
  "data": {
    "content": [...],
    "page": 0,
    "size": 20,
    "totalElements": 154,
    "totalPages": 8,
    "last": false
  },
  "error": null,
  "timestamp": "2026-05-01T10:00:00Z"
}
```

Use Spring's `Pageable` with `@PageableDefault(size = 20, sort = "createdAt", direction = DESC)`.

---

## 8. Security Architecture

### 8.1 JWT Strategy

- **Access token**: 15 minutes TTL, signed with RS256 (private key)
- **Refresh token**: 7 days TTL, stored in Redis with user binding
- **Logout**: refresh token added to Redis blacklist (TTL = remaining validity)
- **Token rotation**: each refresh issues a new refresh token, invalidates the old

```java
// JWT Claims
{
  "sub": "uuid",
  "role": "USER",
  "status": "VERIFIED",
  "iat": 1234567890,
  "exp": 1234568790
}
```

### 8.2 Authorization Rules

All rules enforced at service level using `@PreAuthorize` and manual checks:

| Operation | Rule |
|-----------|------|
| Create listing | VERIFIED user only (status check) |
| Update listing | Owner only |
| Approve/reject booking | Listing owner only |
| Confirm deposit | Listing owner only |
| Upload deposit proof | Booking renter only |
| Read messages | Booking participants only |
| Create review | Booking participant, booking COMPLETED, within 30 days |
| Admin endpoints | ADMIN role only |

### 8.3 Rate Limiting (Redis-backed)

```java
// Applied via custom filter or interceptor
public class RateLimitConfig {
    public static final int DEFAULT_RPM = 60;         // per IP
    public static final int AUTH_RPM = 5;             // login attempts per IP per 15 min
    public static final int OTP_PER_HOUR = 3;         // OTP requests per phone per hour
    public static final int LISTING_CREATE_PER_DAY = 10; // listings per user per day
}
```

### 8.4 Input Validation

Every request DTO uses JSR-380 annotations. The controller uses `@Valid`. The global exception handler catches `MethodArgumentNotValidException` and returns structured field errors.

```java
public record CreateListingRequest(
    @NotBlank @Size(min = 5, max = 120) String title,
    @NotBlank @Size(min = 20, max = 2000) String description,
    @NotNull UUID categoryId,
    @NotNull ListingType type,
    @NotNull @DecimalMin("1.0") BigDecimal pricePerUnit,
    @NotNull PriceUnit priceUnit,
    @NotBlank String district,
    @DecimalMin("0.0") BigDecimal depositAmount
) {}
```

---

## 9. Database Strategy

### 9.1 Migrations with Flyway

All schema changes live in `src/main/resources/db/migration/`. Files are named `V{version}__{description}.sql`. Never edit an already-applied migration — create a new one.

```
V001__initial_schema.sql        → users, categories tables
V002__listing_schema.sql        → listings, product_details, service_details, images
V003__booking_schema.sql        → bookings with overlap constraint
V004__review_message_schema.sql → reviews, messages
V005__triggers.sql              → all business rule triggers
V006__indexes.sql               → all performance indexes
V007__seed_categories.sql       → initial category data
```

### 9.2 Booking Overlap Prevention

This is enforced at the database level using a PostgreSQL exclusion constraint with GiST:

```sql
-- Requires btree_gist extension
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
ADD CONSTRAINT no_overlapping_bookings
EXCLUDE USING gist (
    listing_id WITH =,
    daterange(start_date, end_date, '[]') WITH &&
)
WHERE (status NOT IN ('CANCELLED', 'REJECTED'));
```

This makes double-booking physically impossible regardless of race conditions or application-level bugs.

### 9.3 Critical DB Triggers

#### Prevent Self-Booking
```sql
CREATE OR REPLACE FUNCTION prevent_self_booking()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM listings
        WHERE id = NEW.listing_id AND owner_id = NEW.renter_id
    ) THEN
        RAISE EXCEPTION 'Cannot book your own listing';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_self_booking
BEFORE INSERT ON bookings
FOR EACH ROW EXECUTE FUNCTION prevent_self_booking();
```

#### Enforce Booking State Transitions
```sql
CREATE OR REPLACE FUNCTION enforce_booking_transitions()
RETURNS TRIGGER AS $$
BEGIN
    IF NOT (
        (OLD.status = 'REQUESTED'        AND NEW.status IN ('APPROVED', 'REJECTED', 'CANCELLED')) OR
        (OLD.status = 'APPROVED'         AND NEW.status IN ('DEPOSIT_PENDING', 'CANCELLED')) OR
        (OLD.status = 'DEPOSIT_PENDING'  AND NEW.status IN ('ACTIVE', 'CANCELLED')) OR
        (OLD.status = 'ACTIVE'           AND NEW.status IN ('COMPLETED', 'CANCELLED')) OR
        (OLD.status = NEW.status)
    ) THEN
        RAISE EXCEPTION 'Invalid booking transition: % → %', OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

#### Review Time Window Enforcement
```sql
CREATE OR REPLACE FUNCTION enforce_review_window()
RETURNS TRIGGER AS $$
DECLARE
    completed_at TIMESTAMP;
BEGIN
    SELECT b.updated_at INTO completed_at
    FROM bookings b
    WHERE b.id = NEW.booking_id AND b.status = 'COMPLETED';

    IF completed_at IS NULL THEN
        RAISE EXCEPTION 'Can only review completed bookings';
    END IF;

    IF NOW() > completed_at + INTERVAL '30 days' THEN
        RAISE EXCEPTION 'Review window has expired (30 days after completion)';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

### 9.4 Full-Text Search Setup

```sql
-- Add tsvector column for listings
ALTER TABLE listings ADD COLUMN search_vector tsvector;

-- Maintain automatically
CREATE OR REPLACE FUNCTION update_listing_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('english',
        coalesce(NEW.title, '') || ' ' ||
        coalesce(NEW.description, '') || ' ' ||
        coalesce(NEW.district, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_listing_search_vector
BEFORE INSERT OR UPDATE ON listings
FOR EACH ROW EXECUTE FUNCTION update_listing_search_vector();

CREATE INDEX idx_listings_fts ON listings USING gin(search_vector);
```

### 9.5 Repository Patterns

Prefer JPQL for simple queries, native SQL for complex analytical queries:

```java
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    // Check overlapping bookings (application-level pre-check before DB constraint)
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.listing.id = :listingId
        AND b.status NOT IN ('CANCELLED', 'REJECTED')
        AND b.startDate <= :endDate
        AND b.endDate >= :startDate
    """)
    boolean existsOverlap(UUID listingId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT b FROM Booking b WHERE b.renter.id = :userId ORDER BY b.createdAt DESC")
    Page<Booking> findByRenter(UUID userId, Pageable pageable);

    @Query("SELECT b FROM Booking b WHERE b.listing.owner.id = :ownerId ORDER BY b.createdAt DESC")
    Page<Booking> findByOwner(UUID ownerId, Pageable pageable);
}
```

---

## 10. File Upload Strategy

All file uploads go through Cloudinary (free tier, 25GB storage, transform API).

```java
@Service
public class CloudinaryStorageService {

    public String upload(MultipartFile file, String folder) {
        try {
            Map<String, Object> options = ObjectUtils.asMap(
                "folder", "rentle/" + folder,
                "resource_type", "image",
                "transformation", List.of(
                    Map.of("quality", "auto", "fetch_format", "auto"),
                    Map.of("width", 1200, "crop", "limit")
                )
            );
            Map result = cloudinary.uploader().upload(file.getBytes(), options);
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw new RentleException("File upload failed");
        }
    }
}
```

**Upload rules:**
- Listing images: max 5 per listing, max 10MB each, JPEG/PNG/WebP only
- Profile photos: max 2MB
- Citizenship card: max 5MB, stored in private Cloudinary folder (not publicly accessible)
- Deposit proof screenshots: max 5MB, stored per booking

---

## 11. Notification Strategy

### 11.1 Phase 1: Synchronous SMS + Email

No message queue in Phase 1. Notifications are sent synchronously (or in a `@Async` thread) after the main transaction commits.

```java
@Component
public class BookingNotificationListener {

    @EventListener
    @Async
    public void onBookingCreated(BookingCreatedEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId()).orElseThrow();
        smsService.send(
            booking.getListing().getOwner().getPhoneNumber(),
            "You have a new booking request for '%s'. Open Rentle to approve."
                .formatted(booking.getListing().getTitle())
        );
    }

    @EventListener
    @Async
    public void onBookingApproved(BookingApprovedEvent event) {
        // notify renter
    }

    @EventListener
    @Async
    public void onBookingCompleted(BookingCompletedEvent event) {
        // notify both parties to leave review
    }
}
```

### 11.2 SMS Provider — Sparrow SMS

```java
@Service
public class SparrowSmsService implements SmsService {

    // POST https://api.sparrowsms.com/v2/sms/
    // { token, from, to, text }
    public void send(String to, String message) {
        // HTTP call to Sparrow API
    }
}
```

---

## 12. Error Handling

### 12.1 Exception Hierarchy

```
RentleException (base, unchecked)
├── ResourceNotFoundException      → 404
├── UnauthorizedException          → 401/403
├── InvalidStateTransitionException → 409
├── BookingConflictException        → 409
└── ValidationException             → 400
```

### 12.2 Global Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(NOT_FOUND)
    public ApiResponse<?> handleNotFound(ResourceNotFoundException ex) {
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(BAD_REQUEST)
    public ApiResponse<?> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(toMap(FieldError::getField, FieldError::getDefaultMessage));
        return ApiResponse.error("Validation failed: " + fieldErrors);
    }

    @ExceptionHandler(BookingConflictException.class)
    @ResponseStatus(CONFLICT)
    public ApiResponse<?> handleConflict(BookingConflictException ex) {
        return ApiResponse.error(ex.getMessage());
    }
}
```

---

## 13. Testing Strategy

### 13.1 What to Test

| Layer | Type | Tool |
|-------|------|------|
| State machine logic | Unit | JUnit 5 |
| Pricing calculation | Unit | JUnit 5 |
| Availability check | Unit | JUnit 5 |
| Booking flow end-to-end | Integration | Testcontainers + MockMvc |
| Auth flow | Integration | Testcontainers + MockMvc |
| DB constraints (overlap, triggers) | Integration | Testcontainers |

### 13.2 Integration Test Setup

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("rentle_test")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

### 13.3 Critical Test Cases

```java
// These must pass before any deployment
@Test void shouldPreventDoubleBooking()
@Test void shouldPreventSelfBooking()
@Test void shouldRejectInvalidStateTransition()
@Test void shouldNotAllowReviewAfter30Days()
@Test void shouldNotAllowNonParticipantToReadMessages()
@Test void shouldCalculatePriceCorrectlyForAllPriceUnits()
@Test void shouldRequireVerifiedStatusToCreateListing()
```

---

## 14. Infrastructure & Deployment

### 14.1 docker-compose.yml (Local Dev)

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: rentle
      POSTGRES_USER: rentle
      POSTGRES_PASSWORD: dev_password
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  redis_data:
```

### 14.2 application.yml Key Config

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

  jpa:
    hibernate:
      ddl-auto: validate         # NEVER 'update' or 'create' in any env
    properties:
      hibernate.default_batch_fetch_size: 20

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false

jwt:
  private-key: ${JWT_PRIVATE_KEY}
  public-key: ${JWT_PUBLIC_KEY}
  access-token-expiry-ms: 900000
  refresh-token-expiry-ms: 604800000

cloudinary:
  cloud-name: ${CLOUDINARY_CLOUD_NAME}
  api-key: ${CLOUDINARY_API_KEY}
  api-secret: ${CLOUDINARY_API_SECRET}

sms:
  sparrow:
    token: ${SPARROW_SMS_TOKEN}
    from: Rentle

rentle:
  platform-fee-percent: 7
  review-window-days: 30
  otp-expiry-minutes: 10
  max-listing-images: 5
```

### 14.3 Phase 1 Deployment Target

For Phase 1, a single DigitalOcean Droplet (or AWS EC2 t3.small) with Docker Compose is sufficient:

```
1 Droplet / EC2 instance
├── Spring Boot app container
├── PostgreSQL container (or managed DB)
├── Redis container (or managed Redis)
└── Nginx (reverse proxy + SSL via Certbot)
```

This handles comfortably up to ~5,000 requests/day which is well above Phase 1 expectations.

---

## 15. Development Environment Setup

```bash
# 1. Clone the repo
git clone https://github.com/mesubash/rentle-backend
cd rentle-backend

# 2. Start dependencies
docker-compose up -d

# 3. Copy env template
cp .env.example .env
# Fill in: JWT keys, Cloudinary, Sparrow SMS, SendGrid

# 4. Run migrations (automatic on startup via Flyway)
# 5. Start the application
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 6. API docs available at
open http://localhost:8080/swagger-ui.html
```

---

## 16. Phase 2 Readiness Checklist

These are things to do correctly in Phase 1 so Phase 2 isn't painful:

- [ ] `Payment` table exists in schema from day one (even if unused — no FK gaps later)
- [ ] `Booking.totalPrice` and `Booking.depositAmount` are already stored (payment gateway just confirms them)
- [ ] `Listing.type` enum is extensible (don't hardcode product/service checks in multiple places)
- [ ] `PriceUnit` enum covers `PER_DAY`, `PER_HOUR`, `FLAT` — sufficient for all planned categories
- [ ] Event publishing is already wired — adding RabbitMQ in Phase 2 is a config change, not a rewrite
- [ ] `SmsService` is an interface — swapping Sparrow for Twilio is one class change
- [ ] Search queries use a repository method — swapping PostgreSQL FTS for Elasticsearch is one service change
- [ ] All IDs are UUID — safe for sharding and distributed systems later
- [ ] `UserStatus.SUSPENDED` is enforced at JWT generation time — not just at controller level

---

*This document is the authoritative backend implementation reference for Rentle Phase 1. Any deviation from the patterns defined here — particularly around cross-module communication, state machine enforcement, or database constraint strategy — should be discussed and documented before implementation.*
