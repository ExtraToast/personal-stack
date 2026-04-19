package com.jorisjonkers.personalstack.auth.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ServicePermissionTest {
    @ParameterizedTest(name = "fromHost({0}) = {1}")
    @CsvSource(
        "vault.jorisjonkers.dev, VAULT",
        "vault.jorisjonkers.test, VAULT",
        "stalwart.jorisjonkers.dev, MAIL",
        "stalwart.jorisjonkers.test, MAIL",
        "n8n.jorisjonkers.dev, N8N",
        "grafana.jorisjonkers.dev, GRAFANA",
        "dashboard.jorisjonkers.dev, DASHBOARD",
        "dashboard.jorisjonkers.test, DASHBOARD",
        "traefik.jorisjonkers.dev, TRAEFIK",
        "traefik.jorisjonkers.test, TRAEFIK",
        "assistant.jorisjonkers.dev, ASSISTANT",
        "status.jorisjonkers.dev, STATUS",
        "jellyfin.jorisjonkers.dev, JELLYFIN",
        "jellyseerr.jorisjonkers.dev, JELLYSEERR",
        "sonarr.jorisjonkers.dev, SONARR",
        "radarr.jorisjonkers.dev, RADARR",
        "bazarr.jorisjonkers.dev, BAZARR",
        "prowlarr.jorisjonkers.dev, PROWLARR",
        "qbittorrent.jorisjonkers.dev, QBITTORRENT",
    )
    fun `fromHost resolves production and local dev hostnames`(
        host: String,
        expectedName: String,
    ) {
        val result = ServicePermission.fromHost(host)
        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo(expectedName)
    }

    @Test
    fun `fromHost returns null for unknown host`() {
        assertThat(ServicePermission.fromHost("unknown.jorisjonkers.dev")).isNull()
    }

    @Test
    fun `fromHost returns null for null input`() {
        assertThat(ServicePermission.fromHost(null)).isNull()
    }

    @Test
    fun `fromHost returns null for blank input`() {
        assertThat(ServicePermission.fromHost("")).isNull()
    }

    @Test
    fun `fromHost is case-insensitive`() {
        assertThat(ServicePermission.fromHost("VAULT.jorisjonkers.dev")).isEqualTo(ServicePermission.VAULT)
    }
}
