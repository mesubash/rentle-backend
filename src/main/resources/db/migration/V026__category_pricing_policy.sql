-- Admin-configurable pricing policy per category (docs/13). Deposit guidance bands (keyed to
-- declared item value) and a time-before-start cancellation schedule. Both are JSONB so the
-- shape can grow without migrations. The cancellation schedule is snapshotted onto each booking
-- at request time (below) so the terms shown are the terms recorded.
CREATE TABLE category_pricing_policy (
    category_id        UUID PRIMARY KEY REFERENCES categories(id),
    deposit_bands      JSONB NOT NULL DEFAULT '[]',   -- [{minValue,maxValue,depositMin,depositMax,damageCap}]
    cancellation_tiers JSONB NOT NULL DEFAULT '[]',   -- [{hoursBefore,withholdPct}]
    updated_by         UUID,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The cancellation schedule in force when the booking was requested (arbitration reference).
ALTER TABLE bookings ADD COLUMN cancellation_schedule JSONB;
