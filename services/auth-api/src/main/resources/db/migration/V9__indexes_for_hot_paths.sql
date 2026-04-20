-- Spring Authorization Server's JdbcOAuth2AuthorizationService looks
-- up tokens by (principal_name, registered_client_id) in the
-- introspection / refresh / consent paths. V4 left the
-- oauth2_authorization table without any secondary indexes, so
-- every /oauth2/introspect and token-refresh sequential-scans the
-- table. Not a visible problem today, but the table grows
-- unboundedly with every downstream login and we'd rather not
-- wait for it to bite.
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_principal_client
    ON oauth2_authorization (principal_name, registered_client_id);

-- Note on tables we intentionally did not touch:
--   * app_user: V1 already indexes (username) and (email) — queries
--     are case-sensitive equality, so LOWER() expression indexes
--     would be load-bearing only if the domain layer starts
--     normalising case. It doesn't today.
--   * user_service_permissions: PK is (user_id, service) — the
--     leading-column rule means `WHERE user_id = ?` already uses
--     the existing btree.
