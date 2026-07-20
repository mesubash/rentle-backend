-- Category field-template engine (docs/12 §3): admin-defined fields per category, in three
-- scopes — VERIFICATION (provider credentials, docs/07), LISTING (extra listing fields), and
-- BOOKING (extra request fields). One row per (category, scope, version); answers are stored as
-- JSONB on listings/bookings and validated against the template at write time.

CREATE TABLE category_field_template (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES categories(id),
    scope       VARCHAR(20) NOT NULL CHECK (scope IN ('VERIFICATION', 'LISTING', 'BOOKING')),
    version     INT NOT NULL DEFAULT 1,
    fields      JSONB NOT NULL DEFAULT '[]',
    updated_by  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (category_id, scope, version)
);

-- Answers to LISTING / BOOKING templates (+ the template version answered, for grandfathering).
ALTER TABLE listings ADD COLUMN attributes JSONB NOT NULL DEFAULT '{}';
ALTER TABLE listings ADD COLUMN attributes_template_version INT;
ALTER TABLE bookings ADD COLUMN attributes JSONB NOT NULL DEFAULT '{}';
ALTER TABLE bookings ADD COLUMN attributes_template_version INT;
