-- P1-27: a mere booking REQUEST should not exclusively lock the dates. Previously the
-- exclusion constraint counted REQUESTED, so one unpaid request blocked every competing
-- request (and could grief-lock festival dates for free). Now only APPROVED and later
-- statuses occupy the calendar; multiple pending requests may coexist and the owner picks
-- one to approve. Exclusivity begins at approval (the constraint still guarantees no two
-- APPROVED+ bookings overlap).

ALTER TABLE bookings DROP CONSTRAINT no_overlapping_bookings;

ALTER TABLE bookings
ADD CONSTRAINT no_overlapping_bookings
EXCLUDE USING gist (
    listing_id WITH =,
    tsrange(
        (start_date + COALESCE(start_time, TIME '00:00')),
        CASE WHEN end_time IS NULL
             THEN (end_date + INTERVAL '1 day')
             ELSE (end_date + end_time)
        END,
        '[)'
    ) WITH &&
)
WHERE (status NOT IN ('CANCELLED', 'REJECTED', 'REQUESTED'));
