-- A listing's public rating must reflect only reviews *about the listing/owner*
-- (renter → owner), not the owner's reviews *of the renter*. Both directions
-- share listing_id, so the original aggregate double-counted and let a renter's
-- low score drag down the listing. Scope the listing aggregate to reviews whose
-- subject is the listing owner. User trust_score (already per subject) is unchanged.

CREATE OR REPLACE FUNCTION fn_update_listing_rating()
RETURNS TRIGGER AS $$
DECLARE v_owner UUID;
BEGIN
    SELECT owner_id INTO v_owner FROM listings WHERE id = NEW.listing_id;

    UPDATE listings
    SET
        average_rating = (
            SELECT ROUND(AVG(rating)::numeric, 2)
            FROM reviews
            WHERE listing_id = NEW.listing_id AND subject_id = v_owner
        ),
        review_count = (
            SELECT COUNT(*)
            FROM reviews
            WHERE listing_id = NEW.listing_id AND subject_id = v_owner
        )
    WHERE id = NEW.listing_id;

    UPDATE users
    SET trust_score = (
        SELECT ROUND(AVG(rating)::numeric, 2)
        FROM reviews
        WHERE subject_id = NEW.subject_id
    )
    WHERE id = NEW.subject_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Recompute existing listing aggregates under the corrected rule.
UPDATE listings l
SET average_rating = sub.avg_rating,
    review_count   = sub.cnt
FROM (
    SELECT r.listing_id,
           ROUND(AVG(r.rating)::numeric, 2) AS avg_rating,
           COUNT(*)                          AS cnt
    FROM reviews r
    JOIN listings li ON li.id = r.listing_id
    WHERE r.subject_id = li.owner_id
    GROUP BY r.listing_id
) sub
WHERE l.id = sub.listing_id;
