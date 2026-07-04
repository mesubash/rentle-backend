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
