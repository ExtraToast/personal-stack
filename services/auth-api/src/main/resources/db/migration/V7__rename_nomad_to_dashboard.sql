-- The old Nomad web UI was replaced by Headlamp at dashboard.jorisjonkers.dev.
-- Rename any granted permissions so existing users retain access via the new
-- DASHBOARD service name. Also drop the now-removed TRAEFIK_DASHBOARD grants.
UPDATE user_service_permissions SET service = 'DASHBOARD' WHERE service = 'NOMAD';
DELETE FROM user_service_permissions WHERE service = 'TRAEFIK_DASHBOARD';
