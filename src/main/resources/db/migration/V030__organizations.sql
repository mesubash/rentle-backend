-- Multi-tenant organizations. An organization is a first-class provider: it can own listings and
-- fulfil bookings exactly like an individual user, and the marketplace treats both identically.
-- A user acts either as themselves or, via the account switcher, as an organization they belong to.
--
-- Authorization rides on the existing IAM. Each organization owns a scope (scopes.type='ORG'), and
-- membership is a scoped assignment (assignments.scope_id = the org's scope). Org roles are ordinary
-- IAM roles (ORG_OWNER / ORG_ADMIN / ORG_STAFF) built from organization.* permission keys and remain
-- admin-editable — nothing about membership is hardcoded.
--
-- Two distinct internal concepts:
--   * members  = user accounts holding an org-scoped assignment (they can act as the org).
--   * workers  = labour records (name / phone / role, not accounts) the org assigns to bookings.
-- Customers never see either; who attends a booking is entirely an internal org decision.

CREATE TABLE organizations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(140) NOT NULL,
    bio         VARCHAR(500),
    logo_url    VARCHAR(500),
    scope_id    UUID         NOT NULL REFERENCES scopes(id),   -- the org's IAM scope (1:1)
    created_by  UUID         NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_organizations_slug  UNIQUE (slug),
    CONSTRAINT uq_organizations_scope UNIQUE (scope_id)
);

-- Pending invites only. Once accepted, membership lives as an IAM assignment at the org scope,
-- so this row is deleted on accept. Invitees may not have an account yet (hence email, not user id).
CREATE TABLE organization_invites (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email         VARCHAR(100) NOT NULL,
    role_id       UUID         NOT NULL REFERENCES roles(id),
    token         VARCHAR(64)  NOT NULL,
    invited_by    UUID         NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_org_invite_token UNIQUE (token),
    CONSTRAINT uq_org_invite_email UNIQUE (org_id, email)
);
CREATE INDEX idx_org_invites_org ON organization_invites(org_id);

-- Workers now belong to an organization (previously keyed to a business user account).
ALTER TABLE workers ALTER COLUMN business_id DROP NOT NULL;
ALTER TABLE workers ADD COLUMN org_id UUID REFERENCES organizations(id) ON DELETE CASCADE;
CREATE INDEX idx_workers_org ON workers(org_id, active);

-- A listing / booking is owned by an org when its org column is set; owner_id / renter_id stay as the
-- acting human for audit. "Who may manage this" = the owner user OR a member of that org.
ALTER TABLE listings ADD COLUMN org_id UUID REFERENCES organizations(id);
ALTER TABLE bookings ADD COLUMN provider_org_id UUID REFERENCES organizations(id);
CREATE INDEX idx_listings_org ON listings(org_id);
