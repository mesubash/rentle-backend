-- Trust & safety reports (P1-4): users flag a listing, user, or booking; admins triage.
CREATE TABLE reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id     UUID NOT NULL REFERENCES users(id),
    target_type     VARCHAR(20)  NOT NULL CHECK (target_type IN ('LISTING', 'USER', 'BOOKING')),
    target_id       UUID NOT NULL,
    reason          VARCHAR(1000) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    resolution_note VARCHAR(1000),
    handled_by      UUID REFERENCES users(id),
    handled_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reports_status ON reports(status, created_at);
