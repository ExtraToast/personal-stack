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
        assertThat(fleet.exposureIntent.public).contains("vault")
        assertThat(fleet.exposureIntent.internalOnly).doesNotContain("vault")
    }
}
