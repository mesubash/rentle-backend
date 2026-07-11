-- The original exclusion constraint blocked overlaps at DATE granularity, so two
-- non-overlapping same-day time slots on one listing (e.g. a photographer booked
-- 9-11am and 2-4pm) were wrongly rejected. Replace it with a timestamp-range
-- constraint: day bookings (no times) occupy whole days exactly as before; hourly
-- bookings occupy only their [start, end) window.

ALTER TABLE bookings DROP CONSTRAINT no_overlapping_bookings;

ALTER TABLE bookings
ADD CONSTRAINT no_overlapping_bookings
EXCLUDE USING gist (
    listing_id WITH =,
    tsrange(
        (start_date + COALESCE(start_time, TIME '00:00')),
        CASE WHEN end_time IS NULL
             THEN (end_date + INTERVAL '1 day')   -- whole-day booking: through end of end_date
             ELSE (end_date + end_time)
        END,
        '[)'
    ) WITH &&
)
WHERE (status NOT IN ('CANCELLED', 'REJECTED'));
