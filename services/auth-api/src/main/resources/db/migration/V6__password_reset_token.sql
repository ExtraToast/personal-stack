CREATE TABLE IF NOT EXISTS password_reset_token (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_token_token ON password_reset_token(token);
CREATE INDEX idx_password_reset_token_user_id ON password_reset_token(user_id);
