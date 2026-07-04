-- ============================================================
-- Business-rule triggers. Last line of defence — the Java
-- service layer enforces the same rules first.
-- ============================================================

-- 1. Prevent self-booking
CREATE OR REPLACE FUNCTION fn_prevent_self_booking()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM listings
        WHERE id = NEW.listing_id
        AND owner_id = NEW.renter_id
    ) THEN
        RAISE EXCEPTION 'Cannot book your own listing';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_self_booking
BEFORE INSERT ON bookings
FOR EACH ROW EXECUTE FUNCTION fn_prevent_self_booking();

-- 2. Enforce booking state transitions
CREATE OR REPLACE FUNCTION fn_enforce_booking_transitions()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = NEW.status THEN RETURN NEW; END IF;

    IF NOT (
        (OLD.status = 'REQUESTED'        AND NEW.status IN ('APPROVED', 'REJECTED', 'CANCELLED')) OR
        (OLD.status = 'APPROVED'         AND NEW.status IN ('DEPOSIT_PENDING', 'CANCELLED')) OR
        (OLD.status = 'DEPOSIT_PENDING'  AND NEW.status IN ('ACTIVE', 'CANCELLED')) OR
        (OLD.status = 'ACTIVE'           AND NEW.status IN ('COMPLETED', 'CANCELLED'))
    ) THEN
        RAISE EXCEPTION 'Invalid booking transition: % to %', OLD.status, NEW.status;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_booking_transitions
BEFORE UPDATE ON bookings
FOR EACH ROW EXECUTE FUNCTION fn_enforce_booking_transitions();

-- 3. Enforce 30-day review window on completed bookings
CREATE OR REPLACE FUNCTION fn_enforce_review_window()
RETURNS TRIGGER AS $$
DECLARE v_updated_at timestamptz;
BEGIN
    SELECT updated_at INTO v_updated_at
    FROM bookings
    WHERE id = NEW.booking_id AND status = 'COMPLETED';

    IF v_updated_at IS NULL THEN
        RAISE EXCEPTION 'Can only review completed bookings';
    END IF;

    IF NOW() > v_updated_at + INTERVAL '30 days' THEN
        RAISE EXCEPTION 'Review window expired (30 days after completion)';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_window
BEFORE INSERT ON reviews
FOR EACH ROW EXECUTE FUNCTION fn_enforce_review_window();

-- 4. Recalculate listing rating + subject trust score on new review
CREATE OR REPLACE FUNCTION fn_update_listing_rating()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE listings
    SET
        average_rating = (
            SELECT ROUND(AVG(rating)::numeric, 2)
            FROM reviews
            WHERE listing_id = NEW.listing_id
        ),
        review_count = (
            SELECT COUNT(*) FROM reviews WHERE listing_id = NEW.listing_id
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

CREATE TRIGGER trg_update_listing_rating
AFTER INSERT ON reviews
FOR EACH ROW EXECUTE FUNCTION fn_update_listing_rating();

-- 5. Maintain full-text search vector
CREATE OR REPLACE FUNCTION fn_update_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector := to_tsvector('english',
        coalesce(NEW.title, '') || ' ' ||
        coalesce(NEW.description, '') || ' ' ||
        coalesce(NEW.district, '') || ' ' ||
        coalesce(NEW.location_text, '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_listing_search_vector
BEFORE INSERT OR UPDATE ON listings
FOR EACH ROW EXECUTE FUNCTION fn_update_search_vector();

-- 6. Auto-update updated_at
CREATE OR REPLACE FUNCTION fn_update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TRIGGER trg_listings_updated_at
BEFORE UPDATE ON listings FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TRIGGER trg_bookings_updated_at
BEFORE UPDATE ON bookings FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();
