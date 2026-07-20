-- Provider verification can belong to an organization: when a listing is created as an org, the
-- org's credentials are checked, not the acting member's. org_id null = a personal verification.

ALTER TABLE provider_verification ADD COLUMN org_id UUID REFERENCES organizations(id) ON DELETE CASCADE;

-- Replace the plain (user_id, category_id) uniqueness with scope-aware partial indexes:
-- one personal verification per user+category, one org verification per org+category.
ALTER TABLE provider_verification DROP CONSTRAINT provider_verification_user_id_category_id_key;
CREATE UNIQUE INDEX uq_pv_user_cat ON provider_verification(user_id, category_id) WHERE org_id IS NULL;
CREATE UNIQUE INDEX uq_pv_org_cat  ON provider_verification(org_id, category_id)  WHERE org_id IS NOT NULL;
CREATE INDEX idx_pv_org ON provider_verification(org_id) WHERE org_id IS NOT NULL;
