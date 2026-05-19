CREATE TABLE workspaces (
    id                UUID PRIMARY KEY,
    name              TEXT        NOT NULL,
    repo_url          TEXT,
    branch            TEXT,
    pod_name          TEXT,
    pvc_name          TEXT,
    gateway_endpoint  TEXT,
    status            TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_workspaces_status ON workspaces (status) WHERE status <> 'DESTROYED';
