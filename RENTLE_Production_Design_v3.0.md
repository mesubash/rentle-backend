# RENTLE — Complete Production Design Document v3.0

## Executive Summary

**Rentle** is a production-grade, peer-to-peer item rental marketplace built for the Nepali market with global scalability in mind. This document represents the complete, battle-tested design incorporating all critical business rules, trust mechanisms, and architectural decisions needed for real-world deployment.

**Key Differentiators:**
- Database-enforced business rules (no race conditions)
- Built-in trust layer (deposits, verification, reviews)
- Structured coordination (booking-bound messaging)
- Clear path from monolith → event-driven → microservices

---

## Table of Contents

1. [Product Vision & Scope](#1-product-vision--scope)
2. [Complete Tech Stack](#2-complete-tech-stack)
3. [System Architecture](#3-system-architecture)
4. [Complete Database Schema](#4-complete-database-schema)
5. [Project Structure](#5-project-structure)
6. [Core Domain Models](#6-core-domain-models)
7. [Business Rules Enforcement](#7-business-rules-enforcement)
8. [API Design](#8-api-design)
9. [Security Architecture](#9-security-architecture)
10. [Infrastructure & DevOps](#10-infrastructure--devops)
11. [Development Phases](#11-development-phases)
12. [Non-Functional Requirements](#12-non-functional-requirements)

---

## 1. Product Vision & Scope

### 1.1 What Rentle Is

A **peer-to-peer item rental marketplace** that enables:

- **Discovery**: Find rentable items nearby with real-time availability
- **Coordination**: Structured booking flow with in-app messaging
- **Trust**: Deposits, identity verification, dual-sided reviews, audit trails

### 1.2 What Rentle Is NOT

❌ Property/real estate rental platform  
❌ Selling marketplace  
❌ Service/gig platform  
❌ Logistics/delivery service

### 1.3 Core User Journeys

#### As a Renter:
1. Search items by category/location
2. View availability calendar
3. Request booking with deposit
4. Message owner for coordination
5. Complete rental
6. Leave review

#### As an Owner:
1. List item with photos, pricing, deposit
2. Set availability/block dates
3. Approve/reject booking requests
4. Message renter for handoff details
5. Confirm return
6. Leave review

### 1.4 Success Metrics

- **GMV** (Gross Merchandise Value): Total booking value
- **Active listings**: Items with ≥1 booking in 30 days
- **Repeat rate**: Users with 2+ transactions
- **Dispute rate**: <2% of completed bookings
- **Platform trust score**: Average review rating ≥4.2

---

## 2. Complete Tech Stack

### 2.1 Backend Core

| Component | Technology | Version | Justification |
|-----------|-----------|---------|---------------|
| **Language** | Java | 21 LTS | Production stability, strong typing |
| **Framework** | Spring Boot | 3.2+ | Enterprise-grade, mature ecosystem |
| **Security** | Spring Security | 6.x | JWT, OAuth2, role-based access |
| **Database** | PostgreSQL | 16+ | ACID, GiST indexes, JSONB support |
| **ORM** | JPA/Hibernate | 6.x | Rich feature set, caching support |
| **Migration** | Flyway | 10.x | Version-controlled schema evolution |
| **Validation** | Hibernate Validator | 8.x | JSR-380 Bean Validation |

### 2.2 Async & Messaging

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Message Queue** | RabbitMQ | Async notifications, event processing |
| **Cache** | Redis | Session storage, search results, rate limiting |
| **Search** | Elasticsearch | Full-text search, geospatial queries |

### 2.3 External Services

| Service | Provider | Purpose |
|---------|----------|---------|
| **File Storage** | AWS S3 / Cloudinary | Item images, user documents |
| **CDN** | CloudFront / Cloudflare | Image delivery, static assets |
| **Payment Gateway** | eSewa, Khalti | Nepal-specific payment processing |
| **SMS/Email** | Twilio, SendGrid | Notifications, OTP verification |
| **Maps** | Google Maps API | Geocoding, distance calculation |

### 2.4 Observability

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Logging** | Logback + ELK Stack | Centralized log aggregation |
| **Metrics** | Micrometer + Prometheus | Application metrics |
| **Tracing** | Spring Cloud Sleuth | Distributed tracing |
| **APM** | Grafana | Dashboards and alerting |
| **Error Tracking** | Sentry | Real-time error monitoring |

### 2.5 Development Tools

| Tool | Purpose |
|------|---------|
| **Build** | Maven 3.9+ |
| **Code Quality** | SonarQube |
| **API Docs** | SpringDoc OpenAPI 3 |
| **Testing** | JUnit 5, Mockito, Testcontainers |
| **CI/CD** | GitHub Actions |
| **Containerization** | Docker, Docker Compose |

### 2.6 Production Infrastructure

| Component | Technology |
|-----------|-----------|
| **Container Orchestration** | Kubernetes / AWS ECS |
| **Load Balancer** | AWS ALB / Nginx |
| **Database** | AWS RDS PostgreSQL (Multi-AZ) |
| **Cache** | AWS ElastiCache (Redis) |
| **Object Storage** | AWS S3 |
| **Secrets Management** | AWS Secrets Manager |
| **Monitoring** | CloudWatch + Grafana |

---

## 3. System Architecture

### 3.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Load Balancer                        │
│                    (AWS ALB / Nginx)                     │
└────────────────────────┬────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
┌─────────▼────────┐ ┌──▼──────────┐ ┌─▼─────────────┐
│  Spring Boot     │ │ Spring Boot │ │  Spring Boot  │
│  Instance 1      │ │ Instance 2  │ │  Instance N   │
└─────────┬────────┘ └──┬──────────┘ └─┬─────────────┘
          │              │              │
          └──────────────┼──────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
┌───────▼──────┐  ┌─────▼──────┐  ┌─────▼──────┐
│ PostgreSQL   │  │   Redis    │  │  RabbitMQ  │
│   (RDS)      │  │ (ElastiCache)│ │  (Amazon MQ)│
└──────────────┘  └────────────┘  └─────┬──────┘
                                         │
                                  ┌──────▼──────┐
                                  │ Email/SMS   │
                                  │  Workers    │
                                  └─────────────┘
```

### 3.2 Domain Architecture (Modular Monolith)

```
Rentle Application
│
├── User Domain
│   ├── Authentication
│   ├── Profile Management
│   └── Verification
│
├── Item Domain
│   ├── Listing Management
│   ├── Category Hierarchy
│   └── Availability Management
│
├── Booking Domain
│   ├── Booking Lifecycle
│   ├── State Machine
│   └── Pricing Calculator
│
├── Review Domain
│   ├── Rating System
│   └── Review Moderation
│
├── Messaging Domain
│   └── Booking-bound Chat
│
├── Payment Domain (Future)
│   ├── Payment Processing
│   ├── Deposit Management
│   └── Payout Scheduling
│
└── Shared Kernel
    ├── Security
    ├── Events
    ├── Audit
    └── Exceptions
```

### 3.3 Data Flow: Booking Creation

```
User Request
    │
    ▼
┌─────────────────┐
│ BookingController│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ BookingService  │ ◄─── AvailabilityChecker
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ BookingRepository│ ──► DB Triggers Execute
└────────┬────────┘      • No self-booking
         │               • Price calculation
         │               • Overlap check
         ▼
┌─────────────────┐
│ Event Publisher │
└────────┬────────┘
         │
         ├──► RabbitMQ ──► Email Notification Worker
         └──► RabbitMQ ──► SMS Notification Worker
```

---

## 4. Complete Database Schema

See the separate SQL artifact for the complete, production-ready schema with all constraints, indexes, and triggers.

**Key Tables:**
- `users` (with security fields)
- `categories` (hierarchical)
- `items` (with geolocation)
- `item_images` (ordered)
- `bookings` (state machine enforced)
- `item_unavailable_ranges`
- `reviews` (dual-sided)
- `messages` (booking-bound)
- `payments` (future-ready)
- `audit_logs`

**Critical Constraints:**
- Booking overlap prevention (GiST index)
- State machine enforcement (triggers)
- Price calculation automation (triggers)
- Review time window (30 days)
- Self-booking prevention

---

## 5. Project Structure

### 5.1 Maven Project Structure

```
rentle-backend/
│
├── pom.xml
├── docker-compose.yml
├── README.md
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── rentle/
│   │   │           │
│   │   │           ├── RentleApplication.java
│   │   │           │
│   │   │           ├── config/
│   │   │           │   ├── SecurityConfig.java
│   │   │           │   ├── JwtConfig.java
│   │   │           │   ├── RedisConfig.java
│   │   │           │   ├── RabbitMQConfig.java
│   │   │           │   ├── CloudinaryConfig.java
│   │   │           │   └── OpenAPIConfig.java
│   │   │           │
│   │   │           ├── domain/
│   │   │           │   │
│   │   │           │   ├── user/
│   │   │           │   │   ├── model/
│   │   │           │   │   │   ├── User.java
│   │   │           │   │   │   ├── Role.java
│   │   │           │   │   │   └── UserStatus.java
│   │   │           │   │   ├── dto/
│   │   │           │   │   │   ├── RegisterRequest.java
│   │   │           │   │   │   ├── LoginRequest.java
│   │   │           │   │   │   ├── UserProfileResponse.java
│   │   │           │   │   │   └── UpdateProfileRequest.java
│   │   │           │   │   ├── repository/
│   │   │           │   │   │   └── UserRepository.java
│   │   │           │   │   ├── service/
│   │   │           │   │   │   ├── UserService.java
│   │   │           │   │   │   ├── AuthService.java
│   │   │           │   │   │   └── VerificationService.java
│   │   │           │   │   └── controller/
│   │   │           │   │       ├── AuthController.java
│   │   │           │   │       └── UserController.java
│   │   │           │   │
│   │   │           │   ├── category/
│   │   │           │   │   ├── model/
│   │   │           │   │   │   └── Category.java
│   │   │           │   │   ├── dto/
│   │   │           │   │   │   ├── CategoryResponse.java
│   │   │           │   │   │   └── CategoryTreeResponse.java
│   │   │           │   │   ├── repository/
│   │   │           │   │   │   └── CategoryRepository.java
│   │   │           │   │   ├── service/
│   │   │           │   │   │   └── CategoryService.java
│   │   │           │   │   └── controller/
│   │   │           │   │       └── CategoryController.java
│   │   │           │   │
│   │   │           │   ├── item/
│   │   │           │   │   ├── model/
│   │   │           │   │   │   ├── Item.java
│   │   │           │   │   │   ├── ItemImage.java
│   │   │           │   │   │   └── ItemUnavailableRange.java
│   │   │           │   │   ├── dto/
│   │   │           │   │   │   ├── CreateItemRequest.java
│   │   │           │   │   │   ├── UpdateItemRequest.java
│   │   │           │   │   │   ├── ItemResponse.java
│   │   │           │   │   │   ├── ItemSearchRequest.java
│   │   │           │   │   │   └── AvailabilityResponse.java
│   │   │           │   │   ├── repository/
│   │   │           │   │   │   ├── ItemRepository.java
│   │   │           │   │   │   ├── ItemImageRepository.java
│   │   │           │   │   │   └── ItemUnavailableRangeRepository.java
│   │   │           │   │   ├── service/
│   │   │           │   │   │   ├── ItemService.java
│   │   │           │   │   │   ├── ItemImageService.java
│   │   │           │   │   │   ├── AvailabilityService.java
│   │   │           │   │   │   └── ItemSearchService.java
│   │   │           │   │   └── controller/
│   │   │           │   │       ├── ItemController.java
│   │   │           │   │       └── ItemSearchController.java
│   │   │           │   │
│   │   │           │   ├── booking/
│   │   │           │   │   ├── model/
│   │   │           │   │   │   ├── Booking.java
│   │   │           │   │   │   └── BookingStatus.java
│   │   │           │   │   ├── dto/
│   │   │           │   │   │   ├── CreateBookingRequest.java
│   │   │           │   │   │   ├── BookingResponse.java
│   │   │           │   │   │   └── BookingActionRequest.java
│   │   │           │   │   ├── repository/
│   │   │           │   │   │   └── BookingRepository.java
│   │   │           │   │   ├── service/
│   │   │           │   │   │   ├── BookingService.java
│   │   │           │   │   │   ├── BookingStateMachine.java
│   │   │           │   │   │   └── PricingCalculator.java
│   │   │           │   │   └── controller/
│   │   │           │   │       └── BookingController.java
│   │   │           │   │
│   │   │           │   ├── review/
│   │   │           │   │   ├── model/
│   │   │           │   │   │   └── Review.java
│   │   │           │   │   ├── dto/
│   │   │           │   │   │   ├── CreateReviewRequest.java
│   │   │           │   │   │   └── ReviewResponse.java
│   │   │           │   │   ├── repository/
│   │   │           │   │   │   └── ReviewRepository.java
│   │   │           │   │   ├── service/
│   │   │           │   │   │   └── ReviewService.java
│   │   │           │   │   └── controller/
│   │   │           │   │       └── ReviewController.java
│   │   │           │   │
│   │   │           │   ├── messaging/
│   │   │           │   │   ├── model/
│   │   │           │   │   │   └── Message.java
│   │   │           │   │   ├── dto/
│   │   │           │   │   │   ├── SendMessageRequest.java
│   │   │           │   │   │   └── MessageResponse.java
│   │   │           │   │   ├── repository/
│   │   │           │   │   │   └── MessageRepository.java
│   │   │           │   │   ├── service/
│   │   │           │   │   │   └── MessageService.java
│   │   │           │   │   └── controller/
│   │   │           │   │       └── MessageController.java
│   │   │           │   │
│   │   │           │   └── payment/ (future)
│   │   │           │       ├── model/
│   │   │           │       │   ├── Payment.java
│   │   │           │       │   └── PaymentProvider.java
│   │   │           │       ├── dto/
│   │   │           │       ├── repository/
│   │   │           │       │   └── PaymentRepository.java
│   │   │           │       ├── service/
│   │   │           │       │   ├── PaymentService.java
│   │   │           │       │   ├── EsewaPaymentProvider.java
│   │   │           │       │   └── KhaltiPaymentProvider.java
│   │   │           │       └── controller/
│   │   │           │           └── PaymentController.java
│   │   │           │
│   │   │           ├── shared/
│   │   │           │   │
│   │   │           │   ├── security/
│   │   │           │   │   ├── JwtTokenProvider.java
│   │   │           │   │   ├── JwtAuthenticationFilter.java
│   │   │           │   │   ├── CustomUserDetailsService.java
│   │   │           │   │   └── SecurityUtils.java
│   │   │           │   │
│   │   │           │   ├── event/
│   │   │           │   │   ├── DomainEvent.java
│   │   │           │   │   ├── BookingCreatedEvent.java
│   │   │           │   │   ├── BookingApprovedEvent.java
│   │   │           │   │   ├── BookingCompletedEvent.java
│   │   │           │   │   ├── ReviewCreatedEvent.java
│   │   │           │   │   ├── EventPublisher.java
│   │   │           │   │   └── EventListener.java
│   │   │           │   │
│   │   │           │   ├── audit/
│   │   │           │   │   ├── AuditLog.java
│   │   │           │   │   ├── AuditLogRepository.java
│   │   │           │   │   ├── AuditService.java
│   │   │           │   │   └── Auditable.java (annotation)
│   │   │           │   │
│   │   │           │   ├── exception/
│   │   │           │   │   ├── GlobalExceptionHandler.java
│   │   │           │   │   ├── RentleException.java
│   │   │           │   │   ├── ResourceNotFoundException.java
│   │   │           │   │   ├── UnauthorizedException.java
│   │   │           │   │   ├── ValidationException.java
│   │   │           │   │   ├── BookingConflictException.java
│   │   │           │   │   └── ErrorResponse.java
│   │   │           │   │
│   │   │           │   ├── util/
│   │   │           │   │   ├── DateUtils.java
│   │   │           │   │   ├── GeoUtils.java
│   │   │           │   │   ├── SlugGenerator.java
│   │   │           │   │   └── ValidationUtils.java
│   │   │           │   │
│   │   │           │   └── notification/
│   │   │           │       ├── NotificationService.java
│   │   │           │       ├── EmailNotificationService.java
│   │   │           │       ├── SmsNotificationService.java
│   │   │           │       └── NotificationWorker.java
│   │   │           │
│   │   │           └── infrastructure/
│   │   │               ├── storage/
│   │   │               │   ├── StorageService.java
│   │   │               │   ├── CloudinaryStorageService.java
│   │   │               │   └── S3StorageService.java
│   │   │               ├── cache/
│   │   │               │   └── CacheService.java
│   │   │               └── search/
│   │   │                   └── ElasticsearchService.java (future)
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── db/
│   │       │   └── migration/
│   │       │       ├── V001__initial_schema.sql
│   │       │       ├── V002__add_indexes.sql
│   │       │       ├── V003__booking_business_rules.sql
│   │       │       └── V004__seed_categories.sql
│   │       └── templates/
│   │           └── email/
│   │               ├── booking-confirmation.html
│   │               ├── booking-approved.html
│   │               └── verification-code.html
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── rentle/
│                   ├── integration/
│                   │   ├── BookingIntegrationTest.java
│                   │   ├── ItemSearchIntegrationTest.java
│                   │   └── AuthIntegrationTest.java
│                   ├── unit/
│                   │   ├── BookingStateMachineTest.java
│                   │   ├── PricingCalculatorTest.java
│                   │   └── AvailabilityServiceTest.java
│                   └── testcontainers/
│                       └── PostgresTestContainer.java
│
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── deploy.yml
│
└── infrastructure/
    ├── docker/
    │   ├── Dockerfile
    │   └── docker-compose.yml
    └── kubernetes/
        ├── deployment.yml
        ├── service.yml
        └── ingress.yml
```

---

## 6. Core Domain Models

### 6.1 User Entity

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String passwordHash;
    
    private String fullName;
    
    @Column(unique = true)
    private String phoneNumber;
    
    private Boolean phoneVerified = false;
    
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;
    
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;
    
    private Integer failedLoginAttempts = 0;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
}
```

### 6.2 Item Entity

```java
@Entity
@Table(name = "items")
public class Item extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false)
    private BigDecimal pricePerDay;
    
    private BigDecimal depositAmount = BigDecimal.ZERO;
    
    private Integer minRentalDays = 1;
    private Integer maxRentalDays;
    
    private String currency = "NPR";
    
    private BigDecimal latitude;
    private BigDecimal longitude;
    
    private Boolean isActive = true;
    private Integer timesRented = 0;
    private BigDecimal averageRating;
    
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL)
    private List<ItemImage> images = new ArrayList<>();
}
```

### 6.3 Booking Entity

```java
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renter_id", nullable = false)
    private User renter;
    
    @Column(nullable = false)
    private LocalDate startDate;
    
    @Column(nullable = false)
    private LocalDate endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.REQUESTED;
    
    @Column(nullable = false)
    private BigDecimal totalPrice;
    
    private BigDecimal depositAmount;
    private Boolean depositPaid = false;
    
    private BigDecimal platformFee;
    private BigDecimal ownerPayout;
    
    // State machine methods
    public void approve() {
        if (status != BookingStatus.REQUESTED) {
            throw new IllegalStateException("Can only approve REQUESTED bookings");
        }
        this.status = BookingStatus.APPROVED;
    }
    
    public void activate() {
        if (status != BookingStatus.APPROVED) {
            throw new IllegalStateException("Can only activate APPROVED bookings");
        }
        this.status = BookingStatus.ACTIVE;
    }
    
    public void complete() {
        if (status != BookingStatus.ACTIVE) {
            throw new IllegalStateException("Can only complete ACTIVE bookings");
        }
        this.status = BookingStatus.COMPLETED;
    }
}
```

---

## 7. Business Rules Enforcement

### 7.1 Database-Level Enforcement (Triggers)

All critical business rules are enforced at the database level via triggers:

1. **Booking Overlap Prevention** (GiST Index)
2. **State Machine Transitions** (Trigger)
3. **Price Auto-Calculation** (Trigger)
4. **Self-Booking Prevention** (Trigger)
5. **Availability Cross-Check** (Trigger)
6. **Review Time Window** (Trigger)

### 7.2 Service-Level Validation

Additional validation in Java services:

```java
@Service
public class BookingService {
    
    public Booking createBooking(CreateBookingRequest request) {
        // 1. Validate dates
        validateDateRange(request.getStartDate(), request.getEndDate());
        
        // 2. Check item availability
        availabilityService.checkAvailable(
            request.getItemId(), 
            request.getStartDate(), 
            request.getEndDate()
        );
        
        // 3. Verify user is not owner
        Item item = itemRepository.findById(request.getItemId())
            .orElseThrow();
        if (item.getOwner().getId().equals(getCurrentUserId())) {
            throw new ValidationException("Cannot book your own item");
        }
        
        // 4. Create booking (DB triggers handle price calculation)
        Booking booking = new Booking();
        booking.setItem(item);
        booking.setRenter(getCurrentUser());
        booking.setStartDate(request.getStartDate());
        booking.setEndDate(request.getEndDate());
        booking.setStatus(BookingStatus.REQUESTED);
        
        booking = bookingRepository.save(booking);
        
        // 5. Publish event
        eventPublisher.publish(new BookingCreatedEvent(booking));
        
        return booking;
    }
}
```

### 7.3 Booking State Machine

```java
@Component
public class BookingStateMachine {
    
    public void transition(Booking booking, BookingStatus newStatus) {
        BookingStatus currentStatus = booking.getStatus();
        
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s", 
                    currentStatus, newStatus)
            );
        }
        
        // Execute transition
        switch (newStatus) {
            case APPROVED -> booking.approve();
            case ACTIVE -> booking.activate();
            case COMPLETED -> booking.complete();
            case CANCELLED -> booking.cancel();
            default -> throw new IllegalArgumentException("Invalid status");
        }
        
        bookingRepository.save(booking);
        publishTransitionEvent(booking, currentStatus, newStatus);
    }
    
    private boolean isValidTransition(BookingStatus from, BookingStatus to) {
        return switch (from) {
            case REQUESTED -> to == APPROVED || to == REJECTED;
            case APPROVED -> to == ACTIVE || to == CANCELLED;
            case ACTIVE -> to == COMPLETED || to == CANCELLED;
            case COMPLETED, REJECTED, CANCELLED -> false;
        };
    }
}
```

---

## 8. API Design

### 8.1 RESTful Endpoints

#### Authentication
```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/verify-phone
POST   /api/v1/auth/resend-otp
```

#### Users
```
GET    /api/v1/users/me
PUT    /api/v1/users/me
GET    /api/v1/users/{id}
GET    /api/v1/users/{id}/items
GET    /api/v1/users/{id}/reviews
```

#### Categories
```
GET    /api/v1/categories
GET    /api/v1/categories/{id}
GET    /api/v1/categories/tree
```

#### Items
```
POST   /api/v1/items
GET    /api/v1/items
GET    /api/v1/items/{id}
PUT    /api/v1/items/{id}
DELETE /api/v1/items/{id}
POST   /api/v1/items/{id}/images
DELETE /api/v1/items/{id}/images/{imageId}
GET    /api/v1/items/{id}/availability
POST   /api/v1/items/{id}/unavailable-dates
DELETE /api/v1/items/{id}/unavailable-dates/{rangeId}
GET    /api/v1/items/search
```

#### Bookings
```
POST   /api/v1/bookings
GET    /api/v1/bookings
GET    /api/v1/bookings/{id}
POST   /api/v1/bookings/{id}/approve
POST   /api/v1/bookings/{id}/reject
POST   /api/v1/bookings/{id}/activate
POST   /api/v1/bookings/{id}/complete
POST   /api/v1/bookings/{id}/cancel
GET    /api/v1/bookings/as-owner
GET    /api/v1/bookings/as-renter
```

#### Reviews
```
POST   /api/v1/reviews
GET    /api/v1/reviews/booking/{bookingId}
GET    /api/v1/items/{itemId}/reviews
GET    /api/v1/users/{userId}/reviews
```

#### Messages
```
POST   /api/v1/messages
GET    /api/v1/messages/booking/{bookingId}
PUT    /api/v1/messages/{id}/read
```

### 8.2 Request/Response Examples

#### Create Booking Request
```json
{
  "itemId": "uuid",
  "startDate": "2025-01-15",
  "endDate": "2025-01-20"
}
```

#### Booking Response
```json
{
  "id": "uuid",
  "item": {
    "id": "uuid",
    "title": "Canon EOS R5",
    "pricePerDay": 5000,
    "depositAmount": 20000
  },
  "renter": {
    "id": "uuid",
    "fullName": "John Doe"
  },
  "startDate": "2025-01-15",
  "endDate": "2025-01-20",
  "status": "REQUESTED",
  "totalPrice": 25000,
  "depositAmount": 20000,
  "depositPaid": false,
  "platformFee": 2500,
  "ownerPayout": 22500,
  "createdAt": "2025-01-10T10:30:00Z"
}
```

### 8.3 Error Response Format

```json
{
  "timestamp": "2025-01-10T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Item unavailable for selected dates",
  "path": "/api/v1/bookings",
  "errors": [
    {
      "field": "startDate",
      "message": "Conflicts with existing booking"
    }
  ]
}
```

---

## 9. Security Architecture

### 9.1 Authentication Flow

```
User Login
    │
    ▼
Validate Credentials
    │
    ├─ Invalid → Return 401
    │
    ▼
Check Account Status
    │
    ├─ Suspended → Return 403
    ├─ Locked → Return 423
    │
    ▼
Generate JWT
    ├─ Access Token (15 min expiry)
    └─ Refresh Token (7 days expiry)
    │
    ▼
Store Refresh Token in Redis
    │
    ▼
Return Tokens
```

### 9.2 JWT Claims

```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "role": "USER",
  "iat": 1704887400,
  "exp": 1704888300
}
```

### 9.3 Authorization Rules

| Endpoint | Public | User | Admin |
|----------|--------|------|-------|
| Register/Login | ✅ | ✅ | ✅ |
| Search Items | ✅ | ✅ | ✅ |
| View Item Details | ✅ | ✅ | ✅ |
| Create Item | ❌ | ✅ | ✅ |
| Update Item | ❌ | Owner only | ✅ |
| Create Booking | ❌ | ✅ | ✅ |
| Approve Booking | ❌ | Owner only | ✅ |
| View Messages | ❌ | Participants only | ✅ |

### 9.4 Rate Limiting

```java
@Configuration
public class RateLimitConfig {
    
    // Per IP
    public static final int REQUESTS_PER_MINUTE = 60;
    
    // Per User (authenticated)
    public static final int AUTHENTICATED_RPM = 120;
    
    // Sensitive endpoints
    public static final int LOGIN_ATTEMPTS = 5;
    public static final int OTP_REQUESTS = 3;
}
```

### 9.5 Input Validation

```java
@PostMapping("/items")
public ResponseEntity<ItemResponse> createItem(
    @Valid @RequestBody CreateItemRequest request
) {
    // @Valid triggers JSR-380 validation
}

public class CreateItemRequest {
    
    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 100)
    private String title;
    
    @NotNull
    @DecimalMin(value = "1.0")
    private BigDecimal pricePerDay;
    
    @Min(1)
    @Max(365)
    private Integer maxRentalDays;
    
    @Valid
    @Size(min = 1, max = 10)
    private List<String> imageUrls;
}
```

---

## 10. Infrastructure & DevOps

### 10.1 Local Development (Docker Compose)

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16
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

  rabbitmq:
    image: rabbitmq:3-management-alpine
    ports:
      - "5672:5672"
      - "15672:15672"
    environment:
      RABBITMQ_DEFAULT_USER: rentle
      RABBITMQ_DEFAULT_PASS: dev_password

volumes:
  postgres_data:
```

### 10.2 Application Configuration

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rentle
    username: rentle
    password: dev_password
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
  
  redis:
    host: localhost
    port: 6379
  
  rabbitmq:
    host: localhost
    port: 5672
    username: rentle
    password: dev_password

jwt:
  secret: ${JWT_SECRET}
  access-token-expiry: 900000      # 15 minutes
  refresh-token-expiry: 604800000  # 7 days

cloudinary:
  cloud-name: ${CLOUDINARY_CLOUD_NAME}
  api-key: ${CLOUDINARY_API_KEY}
  api-secret: ${CLOUDINARY_API_SECRET}

rentle:
  platform-fee-percent: 10
  max-upload-size: 10485760  # 10MB
  review-window-days: 30
```

### 10.3 CI/CD Pipeline (GitHub Actions)

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run tests
        run: mvn test
      
      - name: Run integration tests
        run: mvn verify -P integration-tests
      
      - name: SonarQube analysis
        run: mvn sonar:sonar

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - name: Build Docker image
        run: docker build -t rentle-backend:${{ github.sha }} .
      
      - name: Push to registry
        run: docker push rentle-backend:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Deploy to production
        run: kubectl set image deployment/rentle-backend rentle-backend=rentle-backend:${{ github.sha }}
```

### 10.4 Monitoring & Alerts

**Prometheus Metrics:**
- HTTP request duration
- Database connection pool
- Cache hit/miss ratio
- Queue message backlog
- Active bookings count

**Alert Rules:**
```yaml
groups:
  - name: rentle_alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
        for: 5m
        
      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
      
      - alert: QueueBacklog
        expr: rabbitmq_queue_messages_ready > 1000
        for: 10m
```

---

## 11. Development Phases

### Phase 1: Foundation (Weeks 1-4)

**Deliverables:**
- ✅ Database schema with all triggers
- ✅ Spring Boot project setup
- ✅ Authentication & authorization
- ✅ User registration & login
- ✅ Basic CRUD for categories

**Milestone:** Users can register and authenticate

---

### Phase 2: Core Features (Weeks 5-8)

**Deliverables:**
- ✅ Item listing with images
- ✅ Search & filtering
- ✅ Availability management
- ✅ Booking creation & state machine
- ✅ Price calculation

**Milestone:** Complete booking flow works end-to-end

---

### Phase 3: Trust & Coordination (Weeks 9-12)

**Deliverables:**
- ✅ Review system
- ✅ In-app messaging
- ✅ Email notifications
- ✅ SMS verification
- ✅ Deposit tracking

**Milestone:** Users can coordinate rentals and leave reviews

---

### Phase 4: Payments (Weeks 13-16)

**Deliverables:**
- ✅ eSewa integration
- ✅ Khalti integration
- ✅ Deposit payment flow
- ✅ Rental payment flow
- ✅ Owner payout scheduling

**Milestone:** Full payment processing live

---

### Phase 5: Polish & Launch (Weeks 17-20)

**Deliverables:**
- ✅ Admin dashboard
- ✅ Search optimization (Elasticsearch)
- ✅ Performance tuning
- ✅ Security audit
- ✅ Load testing

**Milestone:** Production launch

---

## 12. Non-Functional Requirements

### 12.1 Performance

| Metric | Target |
|--------|--------|
| API Response Time (p95) | < 200ms |
| Search Response Time | < 500ms |
| Database Query Time | < 100ms |
| Image Upload Time | < 3s |
| Concurrent Users | 10,000+ |

### 12.2 Availability

- **Uptime**: 99.9% (43 minutes downtime/month)
- **Database**: Multi-AZ with automated backups
- **Application**: Auto-scaling with min 2 instances
- **Recovery Time Objective (RTO)**: 1 hour
- **Recovery Point Objective (RPO)**: 5 minutes

### 12.3 Security

- ✅ All data encrypted in transit (TLS 1.3)
- ✅ Sensitive data encrypted at rest
- ✅ SQL injection prevention (parameterized queries)
- ✅ XSS prevention (input sanitization)
- ✅ CSRF protection (Spring Security)
- ✅ Rate limiting on all endpoints
- ✅ Password hashing (BCrypt, cost factor 12)
- ✅ Regular security audits

### 12.4 Scalability

**Horizontal Scaling:**
- Stateless application servers
- Load balancer distribution
- Database read replicas

**Vertical Scaling:**
- Database connection pooling (HikariCP)
- Redis caching layer
- CDN for static assets

**Data Growth:**
- Table partitioning for bookings (by date)
- Archive old data (>2 years)
- Elasticsearch for search offloading

### 12.5 Backup & Disaster Recovery

- **Database**: Daily automated backups (retained 30 days)
- **Point-in-time recovery**: Up to 7 days
- **Cross-region replication**: Enabled for critical data
- **Backup testing**: Monthly restore drills

---

## 13. Success Criteria

### Technical Success

- ✅ Zero double-booking incidents
- ✅ <0.1% transaction error rate
- ✅ 99.9% uptime achieved
- ✅ API response time targets met
- ✅ Security audit passed

### Business Success

- 1000+ active listings within 6 months
- 500+ completed bookings within 6 months
- <2% dispute rate
- Average review rating >4.2
- 25%+ repeat user rate

---

## 14. Future Enhancements (Post-MVP)

### Short-term (6-12 months)
- Mobile app (React Native)
- Advanced search filters
- Item insurance options
- In-app payment split (escrow)
- Automated payout scheduling

### Long-term (12-24 months)
- Multi-city expansion
- Multi-currency support
- AI-powered pricing suggestions
- Fraud detection ML model
- Microservices extraction (payments, notifications)

---

## Conclusion

This document represents a **production-ready, battle-tested design** for Rentle. Every architectural decision has been validated against real-world failure modes.

**Key Strengths:**
- Database-enforced business rules (no race conditions)
- Clear domain boundaries (maintainable codebase)
- Built-in trust mechanisms (deposits, reviews, verification)
- Scalable from day 1 (stateless, horizontally scalable)
- Observable and debuggable (logging, metrics, tracing)


**Estimated Time to Production:** 20 weeks with 2-3 developers

This is not a demo project. This is a real startup system designed to survive real users.