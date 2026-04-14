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
        assertThat(fleet.accessIntent.hostLabels["uptime-kuma"]).isEqualTo("status")
    }

    @Test
    fun `rabbitmq is modeled as a public sso protected service`() {
        assertThat(fleet.accessIntent.ssoProtected).contains("rabbitmq")
        assertThat(fleet.accessIntent.hostLabels["rabbitmq"]).isEqualTo("rabbitmq")
        assertThat(fleet.exposureIntent.public).contains("rabbitmq")
        assertThat(fleet.exposureIntent.internalOnly).doesNotContain("rabbitmq")
    }
}
