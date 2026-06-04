-- Store the native CLI session id for observability and future explicit
-- continuation flows. Nullable because:
--   - SHELL sessions have no native CLI id
--   - Codex session id discovery is async (captured after spawn)
--   - Old rows have no id

ALTER TABLE workspace_agent_sessions
    ADD COLUMN cli_session_id TEXT,
    ADD COLUMN run_mode       TEXT NOT NULL DEFAULT 'INTERACTIVE';
