-- The old Nomad web UI was replaced by Headlamp at dashboard.jorisjonkers.dev,
-- and TRAEFIK_DASHBOARD was renamed to TRAEFIK. Rewrite any granted permissions
-- so existing users retain access under the new service names.
UPDATE user_service_permissions SET service = 'DASHBOARD' WHERE service = 'NOMAD';
UPDATE user_service_permissions SET service = 'TRAEFIK' WHERE service = 'TRAEFIK_DASHBOARD';
