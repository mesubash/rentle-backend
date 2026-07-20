-- Per-booking rental agreement (P1-28): owner sets rules on the listing; each booking
-- snapshots the terms in force at request time so both sides have a record of what was agreed.
ALTER TABLE listings ADD COLUMN rental_terms TEXT;
ALTER TABLE bookings ADD COLUMN agreed_terms TEXT;
