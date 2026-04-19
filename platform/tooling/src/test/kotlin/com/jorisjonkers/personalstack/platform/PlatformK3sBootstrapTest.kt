package com.jorisjonkers.personalstack.platform

import com.jorisjonkers.personalstack.platform.inventory.PlatformFleetLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformK3sBootstrapTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val fleet =
        PlatformFleetLoader().load(
            repositoryRoot.resolve("platform/inventory/fleet.yaml"),
        )

    @Test
    fun `k3s bootstrap module configures join token path and cluster firewall ports`() {
        val module = repositoryRoot.resolve("platform/nix/modules/k3s/bootstrap.nix").toFile().readText()

        assertThat(module)
            .contains("apiServerEndpoint")
            .contains("workerJoinTokenFile")
            .contains("--token-file=")
            .contains("allowedTCPPorts")
            .contains("10250")
            .contains("6443")
            .contains("allowedUDPPorts = [ 8472 ]")
            .contains("systemd.tmpfiles.rules")
    }

    @Test
    fun `worker and control plane profiles share the same k3s bootstrap defaults`() {
        val workerProfile = repositoryRoot.resolve("platform/nix/profiles/worker.nix").toFile().readText()
        val controlPlaneProfile = repositoryRoot.resolve("platform/nix/profiles/control-plane.nix").toFile().readText()
        val apiServerEndpoint = fleet.cluster.kubernetes.apiServerEndpoint
        val workerJoinTokenFile = fleet.cluster.kubernetes.workerJoinTokenFile

        assertThat(workerProfile)
            .contains("../modules/k3s/bootstrap.nix")
            .contains("apiServerEndpoint = \"${apiServerEndpoint}\"")
            .contains("workerJoinTokenFile = \"${workerJoinTokenFile}\"")
        assertThat(controlPlaneProfile)
            .contains("../modules/k3s/bootstrap.nix")
            .contains("apiServerEndpoint = \"${apiServerEndpoint}\"")
            .contains("workerJoinTokenFile = \"${workerJoinTokenFile}\"")
    }

    @Test
    fun `bootstrap docs point workers at the token copy helper before deploy`() {
        val bootstrapReadme = repositoryRoot.resolve("platform/cluster/bootstrap/README.md").toFile().readText()
        val installPlaybook = repositoryRoot.resolve("platform/cluster/bootstrap/home-install-playbook.md").toFile().readText()
        val helperScript = repositoryRoot.resolve("platform/scripts/bootstrap/bootstrap-k3s-worker.sh").toFile().readText()

        assertThat(bootstrapReadme).contains("bootstrap-k3s-worker.sh")
        assertThat(installPlaybook)
            .contains("bootstrap-k3s-worker.sh <node-name>")
            .contains("deploy-host.sh <node-name>")
        assertThat(helperScript)
            .contains("K3S_BOOTSTRAP_CONTROL_PLANE_NODE")
            .contains("K3S_CONTROL_PLANE_TOKEN_FILE")
            .contains("K3S_WORKER_JOIN_TOKEN_FILE")
            .contains("platform_ssh_identity_file")
            .contains("require_platform_ssh_identity_file_if_set")
            .contains("sudo cat")
            .contains("sudo tee")
    }
}
