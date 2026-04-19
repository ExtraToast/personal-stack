CREATE TABLE IF NOT EXISTS user_service_permissions (
    user_id UUID    NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    service VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, service)
);
