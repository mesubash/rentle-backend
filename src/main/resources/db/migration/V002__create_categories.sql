CREATE TABLE categories (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id    UUID         REFERENCES categories(id) ON DELETE SET NULL,
    name         VARCHAR(100) NOT NULL,
    slug         VARCHAR(120) NOT NULL,
    listing_type VARCHAR(10)  NOT NULL CHECK (listing_type IN ('PRODUCT', 'SERVICE', 'BOTH')),
    icon_name    VARCHAR(50),
    sort_order   INT          NOT NULL DEFAULT 0,
    is_active    BOOLEAN      NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);
