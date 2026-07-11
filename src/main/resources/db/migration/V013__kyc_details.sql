-- Structured KYC (Tier 1): the citizenship image alone is not enough. A user
-- submits verifiable identity + address details reviewed against front/back
-- citizenship images. On approval the account's name is replaced with the
-- verified real name and these fields become immutable.

CREATE TABLE kyc_details (
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status                      VARCHAR(12)  NOT NULL DEFAULT 'SUBMITTED'
                                    CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),

    real_name                   VARCHAR(120) NOT NULL,
    father_name                 VARCHAR(120) NOT NULL,
    grandfather_name            VARCHAR(120) NOT NULL,
    date_of_birth               DATE         NOT NULL,
    gender                      VARCHAR(10),
    citizenship_number          VARCHAR(40)  NOT NULL,
    citizenship_issue_district  VARCHAR(60)  NOT NULL,
    occupation                  VARCHAR(80)  NOT NULL,

    perm_district               VARCHAR(60)  NOT NULL,
    perm_municipality           VARCHAR(80)  NOT NULL,
    perm_ward                   INT          NOT NULL CHECK (perm_ward BETWEEN 1 AND 35),
    perm_tole                   VARCHAR(120),

    temp_district               VARCHAR(60)  NOT NULL,
    temp_municipality           VARCHAR(80)  NOT NULL,
    temp_ward                   INT          NOT NULL CHECK (temp_ward BETWEEN 1 AND 35),
    temp_tole                   VARCHAR(120),

    front_image_ref             VARCHAR(300) NOT NULL,
    back_image_ref              VARCHAR(300) NOT NULL,

    rejection_reason            VARCHAR(400),
    reviewed_by                 UUID         REFERENCES users(id),
    reviewed_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_kyc_user UNIQUE (user_id)
);

CREATE INDEX idx_kyc_status ON kyc_details(status);

CREATE TRIGGER trg_kyc_updated_at
BEFORE UPDATE ON kyc_details FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();
