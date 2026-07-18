-- Provider verification (docs/07 Phase A): before publishing a SERVICE listing in a category that
-- requires credentials, the provider submits that category's VERIFICATION-scope template answers
-- and an admin approves. Answers are JSONB (validated against the template); the version answered is
-- stored for grandfathering.
CREATE TABLE provider_verification (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id),
    category_id      UUID NOT NULL REFERENCES categories(id),
    status           VARCHAR(12) NOT NULL DEFAULT 'SUBMITTED'
                        CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    fields           JSONB NOT NULL DEFAULT '{}',
    template_version INT,
    rejection_reason VARCHAR(1000),
    reviewed_by      UUID REFERENCES users(id),
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, category_id)
);
CREATE INDEX idx_provider_verification_status ON provider_verification(status, created_at);
