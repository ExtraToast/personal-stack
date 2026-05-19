CREATE TABLE projects (
    id            UUID PRIMARY KEY,
    name          TEXT        NOT NULL,
    slug          TEXT        NOT NULL UNIQUE,
    description   TEXT        NOT NULL DEFAULT '',
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_projects_created_at ON projects (created_at DESC);
