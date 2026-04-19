package com.jorisjonkers.personalstack.auth.domain.model

/**
 * Represents a named service in the personal stack that requires explicit access grants.
 * Each entry declares the subdomain prefixes that map to it. Multiple subdomains are
 * supported to handle alternative hostnames for the same service.
 *
 * ADMIN users bypass all service permission checks.
 * USER/READONLY users require explicit grants stored in user_service_permissions.
 */
enum class ServicePermission(
    vararg subdomains: String,
) {
    VAULT("vault"),
    MAIL("stalwart"),
    N8N("n8n"),
    GRAFANA("grafana"),

    // Replaces the legacy NOMAD entry; the Headlamp kubernetes dashboard now lives at
    // dashboard.jorisjonkers.dev. Existing rows in user_service_permissions are moved
    // across in migration V7.
    DASHBOARD("dashboard"),
    RABBITMQ("rabbitmq"),
    ASSISTANT("assistant"),
    STATUS("status"),

    // Media stack. Surfaced in the app-ui MyApps grid so grantees can land on their
    // Jellyfin / *arr / qBittorrent URLs without memorising subdomains.
    JELLYFIN("jellyfin"),
    JELLYSEERR("jellyseerr"),
    SONARR("sonarr"),
    RADARR("radarr"),
    BAZARR("bazarr"),
    PROWLARR("prowlarr"),
    QBITTORRENT("qbittorrent"),
    ;

    val subdomains: Set<String> = subdomains.toSet()

    companion object {
        private val subdomainIndex: Map<String, ServicePermission> =
            entries
                .flatMap { permission -> permission.subdomains.map { it to permission } }
                .toMap()

        /**
         * Resolves a [ServicePermission] from a hostname such as "vault.jorisjonkers.dev",
         * "dashboard.jorisjonkers.dev", or "stalwart.jorisjonkers.test". Returns null when the
         * host is blank or unrecognised.
         */
        fun fromHost(host: String?): ServicePermission? {
            if (host.isNullOrBlank()) return null
            val subdomain = host.substringBefore(".").lowercase()
            return subdomainIndex[subdomain]
        }
    }
}
