CREATE TABLE reviews (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID        NOT NULL REFERENCES bookings(id),
    author_id   UUID        NOT NULL REFERENCES users(id),
    subject_id  UUID        NOT NULL REFERENCES users(id),
    listing_id  UUID        NOT NULL REFERENCES listings(id),
    rating      INT         NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_review_booking_author UNIQUE (booking_id, author_id)
);

CREATE TABLE messages (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id  UUID        NOT NULL REFERENCES bookings(id),
    sender_id   UUID        NOT NULL REFERENCES users(id),
    content     TEXT        NOT NULL,
    is_read     BOOLEAN     NOT NULL DEFAULT false,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
