package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformStatelessAppsFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `production cluster kustomization includes stateless apps`() {
        val clusterKustomization =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomization.yaml").toFile().readText()
        val clusterKustomizations =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomizations.yaml").toFile().readText()
        val appKustomization =
            repositoryRoot.resolve("platform/cluster/flux/apps/stateless/kustomization.yaml").toFile().readText()

        assertThat(clusterKustomization).contains("- kustomizations.yaml")
        assertThat(clusterKustomizations)
            .contains("name: apps-stateless")
            .contains("path: ./platform/cluster/flux/apps/stateless")
        assertThat(appKustomization)
            .contains("- app-ui")
            .contains("- auth-ui")
            .contains("- assistant-ui")
            .contains("- flaresolverr")
            .contains("- n8n")
    }

    @Test
    fun `flaresolverr stays internal in utility system with a health checked service`() {
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/stateless/flaresolverr/deployment.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/stateless/flaresolverr/namespace.yaml").toFile().readText()

        assertThat(namespace).contains("kind: Namespace").contains("name: utility-system")
        assertThat(manifest)
            .contains("kind: Deployment")
            .contains("name: flaresolverr")
            .contains("namespace: utility-system")
            .contains("21hsmw/flaresolverr:nodriver")
            .contains("containerPort: 8191")
            .contains("path: /health")
            // Pinned to enschede so it co-locates with prowlarr (its only
            // caller) and frees ~500m of CPU request on the saturated
            // frankfurt-contabo-1 node.
            .contains("personal-stack/site: enschede")
            .contains("kind: Service")
            .contains("name: flaresolverr")
            .contains("port: 8191")
    }

    @Test
    fun `wolfmanager runs on the game streaming node behind a cluster service`() {
        val clusterKustomizations =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomizations.yaml").toFile().readText()
        val appKustomization =
            repositoryRoot.resolve("platform/cluster/flux/apps/utility-system/kustomization.yaml").toFile().readText()
        val deployment =
            repositoryRoot.resolve("platform/cluster/flux/apps/utility-system/wolfmanager/deployment.yaml").toFile().readText()
        val service = repositoryRoot.resolve("platform/cluster/flux/apps/utility-system/wolfmanager/service.yaml").toFile().readText()
        val vaultSecrets =
            repositoryRoot.resolve("platform/cluster/flux/apps/utility-system/wolfmanager/vault-secrets.yaml").toFile().readText()

        assertThat(clusterKustomizations)
            .contains("name: apps-utility-system")
            .contains("name: apps-data")
        assertThat(appKustomization).contains("- wolfmanager")
        assertThat(deployment)
            .contains("kind: Deployment")
            .contains("name: wolfmanager")
            .contains("namespace: utility-system")
            .contains("ghcr.io/games-on-whales/wolfmanager/wolfmanager:latest")
            .contains("personal-stack/capability-game-streaming: 'true'")
            .contains("NEXTAUTH_URL")
            .contains("https://wolf.jorisjonkers.dev")
            .contains("WOLF_SOCKET_PATH")
            .contains("/var/run/wolf/wolf.sock")
            .contains("DATABASE_TYPE")
            .contains("value: postgresql")
            .contains("DATABASE_URL")
            .contains("name: wolfmanager-db")
            .contains("/var/run/docker.sock")
            .contains("path: /run/wolf")
            .contains("type: DirectoryOrCreate")
            .contains("/var/lib/personal-stack/wolfmanager/config")
        assertThat(service)
            .contains("kind: Service")
            .contains("name: wolfmanager")
            .contains("port: 3000")
        assertThat(vaultSecrets)
            .contains("kind: ServiceAccount")
            .contains("name: vault-secrets-operator")
            .contains("kind: VaultDynamicSecret")
            .contains("name: wolfmanager-db")
            .contains("mount: database")
            .contains("path: creds/wolfmanager")
            .contains("DATABASE_URL")
            .contains("postgresql://")
            .contains("wolfmanager_db")
            .contains("rolloutRestartTargets")
    }
}
