-- Extensions required by the whole schema
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number          VARCHAR(20)  NOT NULL,
    email                 VARCHAR(100) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(100) NOT NULL,
    profile_photo_url     VARCHAR(500),
    role                  VARCHAR(10)  NOT NULL DEFAULT 'USER'
                              CHECK (role IN ('USER', 'ADMIN')),
    status                VARCHAR(25)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
                              CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'SUSPENDED')),
    phone_verified        BOOLEAN      NOT NULL DEFAULT false,
    citizenship_card_url  VARCHAR(500),
    citizenship_verified  BOOLEAN      NOT NULL DEFAULT false,
    trust_score           NUMERIC(3,2) CHECK (trust_score BETWEEN 1.0 AND 5.0),
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_phone UNIQUE (phone_number),
    CONSTRAINT uq_users_email UNIQUE (email)
);
