# Rentle — Database Schema Design Document
**Version:** 1.0  
**Database:** PostgreSQL 16+  
**Phase:** 1  
**Approach:** Production-grade, minimal, no premature complexity

---

## Table of Contents

1. [Design Principles](#1-design-principles)
2. [Schema Overview](#2-schema-overview)
3. [Tables — Full Specification](#3-tables--full-specification)
4. [Constraints & Business Rules](#4-constraints--business-rules)
5. [Indexes](#5-indexes)
6. [Triggers](#6-triggers)
7. [Migration Files](#7-migration-files)
8. [Complete SQL](#8-complete-sql)

---

## 1. Design Principles

### 1.1 What This Schema Optimises For

1. **Correctness over performance** — business rules enforced at the DB level, not only in application code. A bug in the application cannot produce an invalid booking.
2. **Minimal tables** — only tables that serve Phase 1. No speculative tables for features that don't exist yet.
3. **Extension without migration pain** — the `Listing` + `product_detail` / `service_detail` split means adding new listing types never touches core tables.
4. **UUID primary keys everywhere** — safe for eventual horizontal scaling and prevents ID enumeration.
5. **Soft deletes where needed** — listings and users are soft-deleted (status field), not physically removed. Hard deletes only for audit logs and transient data.

### 1.2 What This Schema Deliberately Excludes (Phase 1)

- No `payments` table processing (table exists, but no gateway integration)
- No geospatial columns (latitude/longitude) — district text field is sufficient for Phase 1 search
- No `audit_logs` table — Phase 1 uses application logging; formal audit table is Phase 2
- No `notifications` table — transient, not stored in Phase 1

---

## 2. Schema Overview

```
users
  └─< listings (owner_id)
        ├── product_details (listing_id, 1:1)
        ├── service_details (listing_id, 1:1)
        └── listing_images (listing_id)
        └── unavailable_ranges (listing_id)

categories
  └─< listings (category_id)
  └─< categories (parent_id, self-ref)

bookings (listing_id, renter_id)
  └─< reviews (booking_id, author_id, subject_id)
  └─< messages (booking_id, sender_id)
  └── payments (booking_id) [table only, Phase 2]
```

**12 tables total** for Phase 1:
`users`, `categories`, `listings`, `product_details`, `service_details`, `listing_images`, `unavailable_ranges`, `bookings`, `reviews`, `messages`, `payments`

---

## 3. Tables — Full Specification

### 3.1 `users`

The central identity table. All actors (renters, owners, service providers, admins) are users.

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK, NOT NULL, DEFAULT gen_random_uuid() | |
| `phone_number` | `varchar(20)` | UNIQUE, NOT NULL | Primary identifier in Nepal |
| `email` | `varchar(100)` | UNIQUE, NOT NULL | Secondary identifier |
| `password_hash` | `varchar(255)` | NOT NULL | BCrypt, cost 12 |
| `full_name` | `varchar(100)` | NOT NULL | |
| `profile_photo_url` | `varchar(500)` | | Cloudinary URL |
| `role` | `varchar(10)` | NOT NULL, DEFAULT 'USER' | USER \| ADMIN |
| `status` | `varchar(25)` | NOT NULL, DEFAULT 'PENDING_VERIFICATION' | PENDING_VERIFICATION \| VERIFIED \| SUSPENDED |
| `phone_verified` | `boolean` | NOT NULL, DEFAULT false | Set true after OTP |
| `citizenship_card_url` | `varchar(500)` | | Cloudinary private URL |
| `citizenship_verified` | `boolean` | NOT NULL, DEFAULT false | Set true by admin |
| `trust_score` | `numeric(3,2)` | CHECK (trust_score BETWEEN 1.0 AND 5.0) | Recalculated on new review |
| `failed_login_attempts` | `int` | NOT NULL, DEFAULT 0 | |
| `locked_until` | `timestamptz` | | Set after 5 failed logins |
| `last_login_at` | `timestamptz` | | |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |
| `updated_at` | `timestamptz` | NOT NULL, DEFAULT now() | Updated by trigger |

---

### 3.2 `categories`

Hierarchical. Phase 1 uses at most 2 levels (parent → child). Parent categories are the top-level type (e.g., "Cameras & Tech"), children are subcategories (e.g., "DSLR Cameras").

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `parent_id` | `uuid` | FK → categories(id), nullable | NULL = top-level |
| `name` | `varchar(100)` | NOT NULL, UNIQUE | |
| `slug` | `varchar(120)` | NOT NULL, UNIQUE | URL-safe, e.g. cameras-tech |
| `listing_type` | `varchar(10)` | NOT NULL | PRODUCT \| SERVICE |
| `icon_name` | `varchar(50)` | | Icon identifier for frontend |
| `sort_order` | `int` | NOT NULL, DEFAULT 0 | Display ordering |
| `is_active` | `boolean` | NOT NULL, DEFAULT true | |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

---

### 3.3 `listings`

The core entity. Everything is a listing. The `type` column determines which detail table applies.

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `owner_id` | `uuid` | FK → users(id), NOT NULL | |
| `category_id` | `uuid` | FK → categories(id), NOT NULL | |
| `type` | `varchar(10)` | NOT NULL | PRODUCT \| SERVICE |
| `status` | `varchar(10)` | NOT NULL, DEFAULT 'DRAFT' | DRAFT \| ACTIVE \| INACTIVE \| REMOVED |
| `title` | `varchar(120)` | NOT NULL | |
| `description` | `text` | NOT NULL | |
| `price_per_unit` | `numeric(10,2)` | NOT NULL, CHECK > 0 | |
| `price_unit` | `varchar(10)` | NOT NULL | PER_DAY \| PER_HOUR \| FLAT |
| `deposit_amount` | `numeric(10,2)` | NOT NULL, DEFAULT 0, CHECK >= 0 | |
| `district` | `varchar(50)` | NOT NULL | Kathmandu, Lalitpur, Pokhara... |
| `location_text` | `varchar(200)` | | "Near Thamel, Kathmandu" |
| `average_rating` | `numeric(3,2)` | CHECK (average_rating BETWEEN 1.0 AND 5.0) | Recalculated on review |
| `review_count` | `int` | NOT NULL, DEFAULT 0 | |
| `total_bookings` | `int` | NOT NULL, DEFAULT 0 | Incremented on COMPLETED |
| `search_vector` | `tsvector` | | Maintained by trigger |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |
| `updated_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

---

### 3.4 `product_details`

One row per product listing. 1:1 with `listings`.

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `listing_id` | `uuid` | FK → listings(id), UNIQUE, NOT NULL | 1:1 enforced |
| `condition` | `varchar(10)` | NOT NULL | NEW \| GOOD \| FAIR |
| `brand` | `varchar(100)` | | |
| `model` | `varchar(100)` | | |
| `min_rental_days` | `int` | NOT NULL, DEFAULT 1, CHECK >= 1 | |
| `max_rental_days` | `int` | CHECK > 0 | NULL = no limit |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

---

### 3.5 `service_details`

One row per service listing. 1:1 with `listings`.

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `listing_id` | `uuid` | FK → listings(id), UNIQUE, NOT NULL | 1:1 enforced |
| `service_area_km` | `int` | CHECK > 0 | Max radius provider covers |
| `typical_duration` | `varchar(15)` | | HOURLY \| HALF_DAY \| FULL_DAY \| CUSTOM |
| `min_notice_hours` | `int` | NOT NULL, DEFAULT 24, CHECK >= 0 | |
| `portfolio_url` | `varchar(500)` | | |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

---

### 3.6 `listing_images`

Ordered images per listing. Max 5 enforced by application layer.

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `listing_id` | `uuid` | FK → listings(id) ON DELETE CASCADE | |
| `url` | `varchar(500)` | NOT NULL | Cloudinary URL |
| `sort_order` | `int` | NOT NULL, DEFAULT 0 | 0 = primary/cover image |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

---

### 3.7 `unavailable_ranges`

Date ranges when a listing is blocked by the owner (owner-initiated unavailability).

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `listing_id` | `uuid` | FK → listings(id) ON DELETE CASCADE, NOT NULL | |
| `start_date` | `date` | NOT NULL | |
| `end_date` | `date` | NOT NULL | |
| `reason` | `varchar(200)` | | Optional — "Already booked offline" |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

CONSTRAINT: `CHECK (end_date >= start_date)`

---

### 3.8 `bookings`

The transactional core. Contains all booking state and pricing data.

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `listing_id` | `uuid` | FK → listings(id), NOT NULL | |
| `renter_id` | `uuid` | FK → users(id), NOT NULL | |
| `start_date` | `date` | NOT NULL | |
| `end_date` | `date` | NOT NULL | |
| `start_time` | `time` | | For hourly/time-slot service bookings |
| `end_time` | `time` | | |
| `status` | `varchar(20)` | NOT NULL, DEFAULT 'REQUESTED' | See status enum |
| `total_price` | `numeric(10,2)` | NOT NULL, CHECK > 0 | Calculated on create |
| `deposit_amount` | `numeric(10,2)` | NOT NULL, DEFAULT 0 | Copied from listing at booking time |
| `deposit_paid` | `boolean` | NOT NULL, DEFAULT false | |
| `deposit_proof_url` | `varchar(500)` | | Screenshot by renter |
| `renter_note` | `varchar(500)` | | |
| `cancellation_reason` | `varchar(500)` | | |
| `cancelled_at` | `timestamptz` | | |
| `cancelled_by` | `uuid` | FK → users(id) | |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |
| `updated_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

**BookingStatus values:**  
`REQUESTED` → `APPROVED` → `DEPOSIT_PENDING` → `ACTIVE` → `COMPLETED`  
`REQUESTED | APPROVED | DEPOSIT_PENDING | ACTIVE` → `CANCELLED`  
`REQUESTED` → `REJECTED`

---

### 3.9 `reviews`

Dual-sided: owner reviews renter, renter reviews owner/listing.

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `booking_id` | `uuid` | FK → bookings(id), NOT NULL | |
| `author_id` | `uuid` | FK → users(id), NOT NULL | Who wrote it |
| `subject_id` | `uuid` | FK → users(id), NOT NULL | Who is reviewed |
| `listing_id` | `uuid` | FK → listings(id), NOT NULL | For listing-level aggregation |
| `rating` | `int` | NOT NULL, CHECK (rating BETWEEN 1 AND 5) | |
| `comment` | `varchar(500)` | | |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

UNIQUE CONSTRAINT: `(booking_id, author_id)` — one review per participant per booking

---

### 3.10 `messages`

Scoped to bookings. No standalone DMs.

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `booking_id` | `uuid` | FK → bookings(id), NOT NULL | |
| `sender_id` | `uuid` | FK → users(id), NOT NULL | |
| `content` | `text` | NOT NULL | |
| `is_read` | `boolean` | NOT NULL, DEFAULT false | |
| `read_at` | `timestamptz` | | |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

---

### 3.11 `payments`

Table exists in Phase 1 schema. Populated in Phase 2 when payment gateway integrations go live.

| Column | Type | Constraints | Notes |
|--------|------|------------|-------|
| `id` | `uuid` | PK | |
| `booking_id` | `uuid` | FK → bookings(id), NOT NULL | |
| `amount` | `numeric(10,2)` | NOT NULL | |
| `currency` | `varchar(5)` | NOT NULL, DEFAULT 'NPR' | |
| `provider` | `varchar(20)` | | ESEWA \| KHALTI \| MANUAL |
| `provider_txn_id` | `varchar(200)` | | External transaction reference |
| `status` | `varchar(20)` | NOT NULL, DEFAULT 'PENDING' | PENDING \| COMPLETED \| FAILED \| REFUNDED |
| `payment_type` | `varchar(20)` | NOT NULL | DEPOSIT \| RENTAL_FEE \| PLATFORM_FEE |
| `created_at` | `timestamptz` | NOT NULL, DEFAULT now() | |
| `updated_at` | `timestamptz` | NOT NULL, DEFAULT now() | |

---

## 4. Constraints & Business Rules

### 4.1 Booking Overlap Prevention (GiST Exclusion)

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
ADD CONSTRAINT no_overlapping_bookings
EXCLUDE USING gist (
    listing_id WITH =,
    daterange(start_date, end_date, '[]') WITH &&
)
WHERE (status NOT IN ('CANCELLED', 'REJECTED'));
```

This is the most critical constraint in the schema. It makes double-booking physically impossible at the database level, regardless of race conditions or concurrent requests.

### 4.2 State Machine Constraint

```sql
ALTER TABLE bookings
ADD CONSTRAINT valid_status_value
CHECK (status IN (
    'REQUESTED', 'APPROVED', 'DEPOSIT_PENDING',
    'ACTIVE', 'COMPLETED', 'CANCELLED', 'REJECTED'
));
```

### 4.3 Self-Reference Prevention

```sql
-- Enforced by trigger (see Section 6)
-- Cannot be expressed as a simple CHECK constraint
```

### 4.4 Review Uniqueness

```sql
ALTER TABLE reviews
ADD CONSTRAINT uq_review_booking_author
UNIQUE (booking_id, author_id);
```

### 4.5 Date Range Validity

```sql
ALTER TABLE bookings ADD CONSTRAINT chk_booking_dates
CHECK (end_date >= start_date);

ALTER TABLE unavailable_ranges ADD CONSTRAINT chk_unavailable_dates
CHECK (end_date >= start_date);
```

---

## 5. Indexes

```sql
-- users
CREATE UNIQUE INDEX idx_users_phone  ON users(phone_number);
CREATE UNIQUE INDEX idx_users_email  ON users(email);
CREATE        INDEX idx_users_status ON users(status);

-- listings
CREATE INDEX idx_listings_owner        ON listings(owner_id);
CREATE INDEX idx_listings_category     ON listings(category_id);
CREATE INDEX idx_listings_type_status  ON listings(type, status);
CREATE INDEX idx_listings_district     ON listings(district);
CREATE INDEX idx_listings_search       ON listings USING gin(search_vector);
CREATE INDEX idx_listings_rating       ON listings(average_rating DESC NULLS LAST)
                                        WHERE status = 'ACTIVE';

-- bookings
CREATE INDEX idx_bookings_listing      ON bookings(listing_id);
CREATE INDEX idx_bookings_renter       ON bookings(renter_id);
CREATE INDEX idx_bookings_status       ON bookings(status);
CREATE INDEX idx_bookings_dates        ON bookings(listing_id, start_date, end_date);

-- messages
CREATE INDEX idx_messages_booking      ON messages(booking_id, created_at DESC);

-- reviews
CREATE INDEX idx_reviews_listing       ON reviews(listing_id);
CREATE INDEX idx_reviews_subject       ON reviews(subject_id);
CREATE INDEX idx_reviews_booking       ON reviews(booking_id);

-- listing_images
CREATE INDEX idx_images_listing        ON listing_images(listing_id, sort_order);

-- unavailable_ranges
CREATE INDEX idx_unavailable_listing   ON unavailable_ranges(listing_id);
```

---

## 6. Triggers

### 6.1 Prevent Self-Booking

```sql
CREATE OR REPLACE FUNCTION fn_prevent_self_booking()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM listings
        WHERE id = NEW.listing_id
        AND owner_id = NEW.renter_id
    ) THEN
        RAISE EXCEPTION 'Cannot book your own listing';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_self_booking
BEFORE INSERT ON bookings
FOR EACH ROW EXECUTE FUNCTION fn_prevent_self_booking();
```

### 6.2 Enforce Booking State Transitions

```sql
CREATE OR REPLACE FUNCTION fn_enforce_booking_transitions()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = NEW.status THEN RETURN NEW; END IF;

    IF NOT (
        (OLD.status = 'REQUESTED'        AND NEW.status IN ('APPROVED', 'REJECTED', 'CANCELLED')) OR
        (OLD.status = 'APPROVED'         AND NEW.status IN ('DEPOSIT_PENDING', 'CANCELLED')) OR
        (OLD.status = 'DEPOSIT_PENDING'  AND NEW.status IN ('ACTIVE', 'CANCELLED')) OR
        (OLD.status = 'ACTIVE'           AND NEW.status IN ('COMPLETED', 'CANCELLED'))
    ) THEN
        RAISE EXCEPTION 'Invalid booking transition: % to %', OLD.status, NEW.status;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_transitions
BEFORE UPDATE ON bookings
FOR EACH ROW EXECUTE FUNCTION fn_enforce_booking_transitions();
```

### 6.3 Enforce Review Time Window

```sql
CREATE OR REPLACE FUNCTION fn_enforce_review_window()
RETURNS TRIGGER AS $$
DECLARE v_updated_at timestamptz;
BEGIN
    SELECT updated_at INTO v_updated_at
    FROM bookings
    WHERE id = NEW.booking_id AND status = 'COMPLETED';

    IF v_updated_at IS NULL THEN
        RAISE EXCEPTION 'Can only review completed bookings';
    END IF;

    IF NOW() > v_updated_at + INTERVAL '30 days' THEN
        RAISE EXCEPTION 'Review window expired (30 days after completion)';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_window
BEFORE INSERT ON reviews
FOR EACH ROW EXECUTE FUNCTION fn_enforce_review_window();
```

### 6.4 Update Listing Rating on New Review

```sql
CREATE OR REPLACE FUNCTION fn_update_listing_rating()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE listings
    SET
        average_rating = (
            SELECT ROUND(AVG(rating)::numeric, 2)
            FROM reviews
            WHERE listing_id = NEW.listing_id
        ),
        review_count = (
            SELECT COUNT(*) FROM reviews WHERE listing_id = NEW.listing_id
        )
    WHERE id = NEW.listing_id;

    UPDATE users
    SET trust_score = (
        SELECT ROUND(AVG(rating)::numeric, 2)
        FROM reviews
        WHERE subject_id = NEW.subject_id
    )
    WHERE id = NEW.subject_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_listing_rating
AFTER INSERT ON reviews
FOR EACH ROW EXECUTE FUNCTION fn_update_listing_rating();
```

### 6.5 Update Search Vector on Listing Change

```sql
CREATE OR REPLACE FUNCTION fn_update_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('english',
        coalesce(NEW.title, '') || ' ' ||
        coalesce(NEW.description, '') || ' ' ||
        coalesce(NEW.district, '') || ' ' ||
        coalesce(NEW.location_text, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_listing_search_vector
BEFORE INSERT OR UPDATE ON listings
FOR EACH ROW EXECUTE FUNCTION fn_update_search_vector();
```

### 6.6 Auto-update `updated_at`

```sql
CREATE OR REPLACE FUNCTION fn_update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply to all relevant tables
CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TRIGGER trg_listings_updated_at
BEFORE UPDATE ON listings FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TRIGGER trg_bookings_updated_at
BEFORE UPDATE ON bookings FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();
```

---

## 7. Migration Files

```
src/main/resources/db/migration/

V001__create_users.sql
V002__create_categories.sql
V003__create_listings.sql
V004__create_bookings.sql
V005__create_reviews_messages.sql
V006__create_payments.sql
V007__add_triggers.sql
V008__add_indexes.sql
V009__seed_categories.sql
```

Each file is idempotent relative to its version. Never edit. Always add.

---

## 8. Complete SQL

```sql
-- ============================================================
-- RENTLE — Production Database Schema (Phase 1)
-- PostgreSQL 16+
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number          VARCHAR(20) NOT NULL,
    email                 VARCHAR(100) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(100) NOT NULL,
    profile_photo_url     VARCHAR(500),
    role                  VARCHAR(10)  NOT NULL DEFAULT 'USER'
                              CHECK (role IN ('USER', 'ADMIN')),
    status                VARCHAR(25)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
                              CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'SUSPENDED')),
    phone_verified        BOOLEAN      NOT NULL DEFAULT false,
    citizenship_card_url  VARCHAR(500),
    citizenship_verified  BOOLEAN      NOT NULL DEFAULT false,
    trust_score           NUMERIC(3,2) CHECK (trust_score BETWEEN 1.0 AND 5.0),
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_phone UNIQUE (phone_number),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ============================================================
-- CATEGORIES
-- ============================================================
CREATE TABLE categories (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id    UUID        REFERENCES categories(id) ON DELETE SET NULL,
    name         VARCHAR(100) NOT NULL,
    slug         VARCHAR(120) NOT NULL,
    listing_type VARCHAR(10)  NOT NULL CHECK (listing_type IN ('PRODUCT', 'SERVICE', 'BOTH')),
    icon_name    VARCHAR(50),
    sort_order   INT          NOT NULL DEFAULT 0,
    is_active    BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

-- ============================================================
-- LISTINGS
-- ============================================================
CREATE TABLE listings (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID          NOT NULL REFERENCES users(id),
    category_id     UUID          NOT NULL REFERENCES categories(id),
    type            VARCHAR(10)   NOT NULL CHECK (type IN ('PRODUCT', 'SERVICE')),
    status          VARCHAR(10)   NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'REMOVED')),
    title           VARCHAR(120)  NOT NULL,
    description     TEXT          NOT NULL,
    price_per_unit  NUMERIC(10,2) NOT NULL CHECK (price_per_unit > 0),
    price_unit      VARCHAR(10)   NOT NULL CHECK (price_unit IN ('PER_DAY', 'PER_HOUR', 'FLAT')),
    deposit_amount  NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (deposit_amount >= 0),
    district        VARCHAR(50)   NOT NULL,
    location_text   VARCHAR(200),
    average_rating  NUMERIC(3,2)  CHECK (average_rating BETWEEN 1.0 AND 5.0),
    review_count    INT           NOT NULL DEFAULT 0,
    total_bookings  INT           NOT NULL DEFAULT 0,
    search_vector   TSVECTOR,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ============================================================
-- PRODUCT DETAILS
-- ============================================================
CREATE TABLE product_details (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id       UUID        NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    condition        VARCHAR(10) NOT NULL CHECK (condition IN ('NEW', 'GOOD', 'FAIR')),
    brand            VARCHAR(100),
    model            VARCHAR(100),
    min_rental_days  INT         NOT NULL DEFAULT 1 CHECK (min_rental_days >= 1),
    max_rental_days  INT         CHECK (max_rental_days > 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_detail_listing UNIQUE (listing_id)
);

-- ============================================================
-- SERVICE DETAILS
-- ============================================================
CREATE TABLE service_details (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id        UUID        NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    service_area_km   INT         CHECK (service_area_km > 0),
    typical_duration  VARCHAR(15) CHECK (typical_duration IN ('HOURLY', 'HALF_DAY', 'FULL_DAY', 'CUSTOM')),
    min_notice_hours  INT         NOT NULL DEFAULT 24 CHECK (min_notice_hours >= 0),
    portfolio_url     VARCHAR(500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_service_detail_listing UNIQUE (listing_id)
);

-- ============================================================
-- LISTING IMAGES
-- ============================================================
CREATE TABLE listing_images (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id  UUID        NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- UNAVAILABLE RANGES
-- ============================================================
CREATE TABLE unavailable_ranges (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id  UUID        NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_unavailable_dates CHECK (end_date >= start_date)
);

-- ============================================================
-- BOOKINGS
-- ============================================================
CREATE TABLE bookings (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id           UUID          NOT NULL REFERENCES listings(id),
    renter_id            UUID          NOT NULL REFERENCES users(id),
    start_date           DATE          NOT NULL,
    end_date             DATE          NOT NULL,
    start_time           TIME,
    end_time             TIME,
    status               VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED'
                             CHECK (status IN (
                                 'REQUESTED', 'APPROVED', 'DEPOSIT_PENDING',
                                 'ACTIVE', 'COMPLETED', 'CANCELLED', 'REJECTED'
                             )),
    total_price          NUMERIC(10,2) NOT NULL CHECK (total_price > 0),
    deposit_amount       NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (deposit_amount >= 0),
    deposit_paid         BOOLEAN       NOT NULL DEFAULT false,
    deposit_proof_url    VARCHAR(500),
    renter_note          VARCHAR(500),
    cancellation_reason  VARCHAR(500),
    cancelled_at         TIMESTAMPTZ,
    cancelled_by         UUID          REFERENCES users(id),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_booking_dates CHECK (end_date >= start_date),
    CONSTRAINT no_overlapping_bookings EXCLUDE USING gist (
        listing_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (status NOT IN ('CANCELLED', 'REJECTED'))
);

-- ============================================================
-- REVIEWS
-- ============================================================
CREATE TABLE reviews (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID        NOT NULL REFERENCES bookings(id),
    author_id   UUID        NOT NULL REFERENCES users(id),
    subject_id  UUID        NOT NULL REFERENCES users(id),
    listing_id  UUID        NOT NULL REFERENCES listings(id),
    rating      INT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_review_booking_author UNIQUE (booking_id, author_id)
);

-- ============================================================
-- MESSAGES
-- ============================================================
CREATE TABLE messages (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID        NOT NULL REFERENCES bookings(id),
    sender_id   UUID        NOT NULL REFERENCES users(id),
    content     TEXT        NOT NULL,
    is_read     BOOLEAN     NOT NULL DEFAULT false,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- PAYMENTS (table present, gateway integration in Phase 2)
-- ============================================================
CREATE TABLE payments (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID          NOT NULL REFERENCES bookings(id),
    amount          NUMERIC(10,2) NOT NULL,
    currency        VARCHAR(5)    NOT NULL DEFAULT 'NPR',
    provider        VARCHAR(20)   CHECK (provider IN ('ESEWA', 'KHALTI', 'MANUAL')),
    provider_txn_id VARCHAR(200),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    payment_type    VARCHAR(20)   NOT NULL
                        CHECK (payment_type IN ('DEPOSIT', 'RENTAL_FEE', 'PLATFORM_FEE')),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ============================================================
-- SEED: PHASE 1 CATEGORIES
-- ============================================================
INSERT INTO categories (id, name, slug, listing_type, icon_name, sort_order) VALUES
(gen_random_uuid(), 'Cameras & Tech',           'cameras-tech',         'PRODUCT', 'camera',    1),
(gen_random_uuid(), 'Traditional Clothing',     'traditional-clothing',  'PRODUCT', 'shirt',     2),
(gen_random_uuid(), 'Moving & Transport',        'moving-transport',      'SERVICE', 'truck',     3),
(gen_random_uuid(), 'Event & Photography',       'event-photography',     'SERVICE', 'aperture',  4);
```

---

*This schema is designed for Phase 1 correctness and Phase 2 extensibility. Before any Phase 2 changes, review the Phase 2 Readiness Checklist in the Backend Technical document.*
