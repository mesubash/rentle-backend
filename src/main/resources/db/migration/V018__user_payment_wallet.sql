-- Owner payment wallet handle (eSewa/Khalti), shown to renters at the deposit step
-- so the off-platform deposit no longer relies on the owner typing it into chat (P1-23).
ALTER TABLE users ADD COLUMN payment_wallet VARCHAR(100);
