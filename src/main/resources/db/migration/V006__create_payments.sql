-- Table present from day one; gateway integration lands in Phase 2.
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
