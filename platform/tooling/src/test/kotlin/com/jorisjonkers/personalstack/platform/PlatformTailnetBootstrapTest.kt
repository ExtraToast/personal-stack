package com.jorisjonkers.personalstack.platform

import com.jorisjonkers.personalstack.platform.inventory.PlatformFleetLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformTailnetBootstrapTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val fleet =
        PlatformFleetLoader().load(
            repositoryRoot.resolve("platform/inventory/fleet.yaml"),
        )

    @Test
    fun `platform inventory no longer models headscale as a planned service`() {
        val hostNativeServices = fleet.serviceIntent.hostNative.values.flatten()
        val placementServices =
            fleet.placementIntent.frankfurtOnly + fleet.placementIntent.enschedeOnly
        val exposedServices =
            fleet.exposureIntent.public +
                fleet.exposureIntent.publicAndLan +
                fleet.exposureIntent.internalOnly +
                fleet.exposureIntent.lanOnly

        assertThat(hostNativeServices).doesNotContain("headscale")
        assertThat(placementServices).doesNotContain("headscale")
        assertThat(exposedServices).doesNotContain("headscale")
        assertThat(fleet.accessIntent.hostLabels).doesNotContainKey("headscale")
    }

    @Test
    fun `bootstrap docs describe Tailscale admin console auth key flow`() {
        val platformReadme = repositoryRoot.resolve("platform/README.md").toFile().readText()
        val bootstrapReadme = repositoryRoot.resolve("platform/cluster/bootstrap/README.md").toFile().readText()
        val tailnetPlaybook =
            repositoryRoot.resolve("platform/cluster/bootstrap/tailscale-tailnet-playbook.md").toFile().readText()

        assertThat(platformReadme)
            .contains("hosted `Tailscale` admin console")
            .contains("bootstrap-tailnet.sh")
        assertThat(bootstrapReadme).contains("tailscale-tailnet-playbook.md")
        assertThat(tailnetPlaybook)
            .contains("one-off auth key")
            .contains("Tailscale admin console")
            .contains("bootstrap-tailnet.sh <node-name>")
            .contains("MagicDNS")
    }

    @Test
    fun `tailnet bootstrap helper expects an auth key and runs tailscale up remotely`() {
        val helperScript = repositoryRoot.resolve("platform/scripts/bootstrap/bootstrap-tailnet.sh").toFile().readText()

        assertThat(helperScript)
            .contains("TS_AUTH_KEY")
            .contains("BOOTSTRAP_SSH_HOST")
            .contains("tailscale up")
            .contains("--auth-key=")
            .contains("--hostname=")
            .contains("tailscale status")
    }
}
