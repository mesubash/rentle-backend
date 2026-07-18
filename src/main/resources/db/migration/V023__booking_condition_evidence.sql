-- Hand-over / return condition evidence (P1-22): photo + note captured at checkout and
-- return so a deposit dispute has a record. Photos live in private storage; only refs here.
ALTER TABLE bookings ADD COLUMN checkout_condition_ref VARCHAR(300);
ALTER TABLE bookings ADD COLUMN checkout_note          VARCHAR(1000);
ALTER TABLE bookings ADD COLUMN return_condition_ref   VARCHAR(300);
ALTER TABLE bookings ADD COLUMN return_note            VARCHAR(1000);
