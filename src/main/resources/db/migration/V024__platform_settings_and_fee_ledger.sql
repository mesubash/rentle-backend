-- Admin-configurable platform settings (Layer 1, docs/12 §2b) + per-booking fee ledger (P0-3).

-- Key/value settings, seeded from application.yml defaults, editable by an admin without a deploy.
CREATE TABLE platform_settings (
    key        VARCHAR(60) PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Per-booking commission snapshot. Frozen at completion so the invoiced amount reflects the
-- rate in force then. Launch decision (docs/13): fee percent is 0 until monetization turns on.
ALTER TABLE bookings ADD COLUMN platform_fee_percent NUMERIC(5,2);
ALTER TABLE bookings ADD COLUMN platform_fee_amount  NUMERIC(10,2);
ALTER TABLE bookings ADD COLUMN fee_invoiced         BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE bookings ADD COLUMN fee_invoiced_at      TIMESTAMPTZ;
