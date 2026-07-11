-- Google sign-in provides an email but no phone or password, so those become
-- optional; email verification is tracked alongside phone verification.

ALTER TABLE users ALTER COLUMN phone_number DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN google_id VARCHAR(64);
ALTER TABLE users ADD CONSTRAINT uq_users_google UNIQUE (google_id);

-- Local accounts created before this change already proved their phone via OTP;
-- treat their email as verified so the new gate doesn't retroactively lock them.
UPDATE users SET email_verified = true WHERE phone_verified = true;
