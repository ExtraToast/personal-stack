-- Add email confirmation requirement to registration flow.
-- Existing users are pre-confirmed; new registrations require confirmation.

ALTER TABLE app_user ADD COLUMN email_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: existing users are already confirmed
UPDATE app_user SET email_confirmed = TRUE;

-- Tokens for email confirmation links
CREATE TABLE email_confirmation_token (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ectoken_token   ON email_confirmation_token(token);
CREATE INDEX idx_ectoken_user_id ON email_confirmation_token(user_id);
