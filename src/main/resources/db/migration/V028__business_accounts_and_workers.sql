-- Business accounts + worker registry (docs/07 Phase B): a BUSINESS account (e.g. a plumbing
-- or photography company) registers workers, and a staff member — not the account holder — may
-- attend a booking. Listings by a business show a "Registered business" badge.

ALTER TABLE users ADD COLUMN account_type VARCHAR(12) NOT NULL DEFAULT 'INDIVIDUAL'
    CHECK (account_type IN ('INDIVIDUAL', 'BUSINESS'));
ALTER TABLE users ADD COLUMN business_name VARCHAR(120);

CREATE TABLE workers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL REFERENCES users(id),
    name        VARCHAR(120) NOT NULL,
    phone       VARCHAR(20),
    role        VARCHAR(80),
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workers_business ON workers(business_id, active);

-- Which worker will attend this booking (set by the business owner), snapshotting the name/phone
-- so the client sees who is coming even if the worker record later changes.
ALTER TABLE bookings ADD COLUMN assigned_worker_id   UUID REFERENCES workers(id);
ALTER TABLE bookings ADD COLUMN assigned_worker_name VARCHAR(120);
