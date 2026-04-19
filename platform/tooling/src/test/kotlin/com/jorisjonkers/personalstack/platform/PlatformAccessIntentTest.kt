package com.jorisjonkers.personalstack.platform

import com.jorisjonkers.personalstack.platform.inventory.PlatformFleetLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformAccessIntentTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val fleet =
        PlatformFleetLoader().load(
            repositoryRoot.resolve("platform/inventory/fleet.yaml"),
        )

    @Test
    fun `vault is modeled as a public sso protected service`() {
        assertThat(fleet.accessIntent.ssoProtected).contains("vault")
        assertThat(fleet.accessIntent.hostLabels["vault"]).isEqualTo("vault")
        assertThat(fleet.exposureIntent.public).contains("vault")
        assertThat(fleet.exposureIntent.internalOnly).doesNotContain("vault")
    }

    @Test
    fun `edge exposed services declare stable host labels`() {
        assertThat(fleet.accessIntent.hostLabels["app-ui"]).isEqualTo("root")
        assertThat(fleet.accessIntent.hostLabels["auth-ui"]).isEqualTo("auth")
        assertThat(fleet.accessIntent.hostLabels["assistant-ui"]).isEqualTo("assistant")
        assertThat(fleet.accessIntent.hostLabels["stalwart"]).isEqualTo("stalwart")
        assertThat(fleet.accessIntent.hostLabels["uptime-kuma"]).isEqualTo("status")
        assertThat(fleet.accessIntent.hostLabels["bazarr"]).isEqualTo("bazarr")
        assertThat(fleet.accessIntent.hostLabels["prowlarr"]).isEqualTo("prowlarr")
        assertThat(fleet.accessIntent.hostLabels["qbittorrent"]).isEqualTo("qbittorrent")
        assertThat(fleet.accessIntent.hostLabels["jellyseerr"]).isEqualTo("jellyseerr")
    }

    @Test
    fun `rabbitmq is modeled as a public sso protected service`() {
        assertThat(fleet.accessIntent.ssoProtected).contains("rabbitmq")
        assertThat(fleet.accessIntent.hostLabels["rabbitmq"]).isEqualTo("rabbitmq")
        assertThat(fleet.exposureIntent.public).contains("rabbitmq")
        assertThat(fleet.exposureIntent.internalOnly).doesNotContain("rabbitmq")
    }

    @Test
    fun `stalwart admin is modeled as a public sso protected service`() {
        assertThat(fleet.accessIntent.ssoProtected).contains("stalwart")
        assertThat(fleet.accessIntent.hostLabels["stalwart"]).isEqualTo("stalwart")
        assertThat(fleet.exposureIntent.public).contains("stalwart")
        assertThat(fleet.exposureIntent.internalOnly).doesNotContain("stalwart")
    }

    @Test
    fun `media tools are public on both edges with external sso enforcement`() {
        assertThat(fleet.exposureIntent.publicAndLan)
            .contains("bazarr", "prowlarr", "qbittorrent", "jellyseerr")
        assertThat(fleet.accessIntent.ssoProtected)
            .contains("bazarr", "prowlarr", "qbittorrent")
            .doesNotContain("jellyseerr")
        assertThat(fleet.exposureIntent.internalOnly)
            .doesNotContain("bazarr", "prowlarr", "qbittorrent", "jellyseerr")
    }
}
