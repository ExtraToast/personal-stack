package com.jorisjonkers.personalstack.auth.domain.model

/**
 * Represents a named service in the personal stack that requires explicit access grants.
 * Each entry declares the subdomain prefixes that map to it. Multiple subdomains are
 * supported to handle production hostnames (e.g. "mail") and local dev names (e.g. "stalwart").
 *
 * ADMIN users bypass all service permission checks.
 * USER/READONLY users require explicit grants stored in user_service_permissions.
 */
enum class ServicePermission(
    vararg subdomains: String,
) {
    VAULT("vault"),
    MAIL("mail", "stalwart"),
    N8N("n8n"),
    GRAFANA("grafana"),
    RABBITMQ("rabbitmq"),
    ASSISTANT("assistant"),
    TRAEFIK_DASHBOARD("traefik"),
    STATUS("status"),
    ;

    val subdomains: Set<String> = subdomains.toSet()

    companion object {
        private val subdomainIndex: Map<String, ServicePermission> =
            entries
                .flatMap { permission -> permission.subdomains.map { it to permission } }
                .toMap()

        /**
         * Resolves a [ServicePermission] from a hostname such as "vault.jorisjonkers.dev",
         * "mail.jorisjonkers.dev", or "stalwart.jorisjonkers.test". Returns null when the host is
         * blank or unrecognised.
         */
        fun fromHost(host: String?): ServicePermission? {
            if (host.isNullOrBlank()) return null
            val subdomain = host.substringBefore(".").lowercase()
            return subdomainIndex[subdomain]
        }
    }
}
