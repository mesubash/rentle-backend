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

CREATE TABLE listing_images (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id  UUID         NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    url         VARCHAR(500) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE unavailable_ranges (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id  UUID        NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    reason      VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_unavailable_dates CHECK (end_date >= start_date)
);
