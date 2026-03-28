package com.jorisjonkers.personalstack.auth.domain.model

/**
 * Represents a named service in the personal stack that requires explicit access grants.
 * The [subdomain] field is the subdomain prefix used in hostname matching (e.g. "vault" matches
 * vault.jorisjonkers.dev and vault.localhost).
 *
 * ADMIN users bypass all service permission checks.
 * USER/READONLY users require explicit grants stored in user_service_permissions.
 */
enum class ServicePermission(
    val subdomain: String,
) {
    VAULT("vault"),
    MAIL("mail"),
    N8N("n8n"),
    GRAFANA("grafana"),
    ASSISTANT("assistant"),
    TRAEFIK_DASHBOARD("traefik"),
    STATUS("status"),
    ;

    companion object {
        /**
         * Resolves a [ServicePermission] from a hostname such as "vault.jorisjonkers.dev"
         * or "vault.localhost". Returns null when the host is blank or unrecognised.
         */
        fun fromHost(host: String?): ServicePermission? {
            if (host.isNullOrBlank()) return null
            val subdomain = host.substringBefore(".").lowercase()
            return entries.find { it.subdomain == subdomain }
        }
    }
}
