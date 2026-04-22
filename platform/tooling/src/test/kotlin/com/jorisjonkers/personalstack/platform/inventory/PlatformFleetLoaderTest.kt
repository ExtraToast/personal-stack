package com.jorisjonkers.personalstack.platform.inventory

import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class PlatformFleetLoaderTest {
    private val loader = PlatformFleetLoader()
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `loads the seeded platform inventory`() {
        val fleet = loader.load(repositoryRoot.resolve("platform/inventory/fleet.yaml"))

        assertThat(fleet.cluster.name).isEqualTo("personal-stack")
        assertThat(fleet.cluster.kubernetes.bootstrapControlPlane).isEqualTo("frankfurt-contabo-1")
        assertThat(fleet.cluster.kubernetes.apiServerEndpoint).isEqualTo("https://167.86.79.203:6443")
        assertThat(fleet.cluster.kubernetes.controlPlaneTokenFile).isEqualTo("/var/lib/rancher/k3s/server/node-token")
        assertThat(fleet.cluster.kubernetes.workerJoinTokenFile).isEqualTo("/var/lib/personal-stack/secrets/k3s/agent-token")
        assertThat(fleet.sites.keys).containsExactlyInAnyOrder("frankfurt", "enschede")
        assertThat(fleet.sites.getValue("enschede").networking?.lanIngressIp).isEqualTo("192.168.0.99")
        assertThat(fleet.sites.getValue("enschede").networking?.wanPublicIp).isEqualTo("130.89.169.227")
        assertThat(fleet.ingressIntent.wanOriginOverrides).containsEntry("jellyfin", "home_direct")
        assertThat(fleet.nodes.keys).contains(
            "frankfurt-contabo-1",
            "enschede-gtx-960m-1",
            "enschede-t1000-1",
            "enschede-pi-1",
            "enschede-pi-2",
            "enschede-pi-3",
        )
        assertThat(fleet.nodes.getValue("frankfurt-contabo-1").ssh?.host).isEqualTo("167.86.79.203")
        assertThat(fleet.nodes.getValue("enschede-t1000-1").ssh?.user).isEqualTo("deploy")
        assertThat(fleet.nodes.getValue("enschede-pi-1").ssh?.host).isEqualTo("enschede-pi-1")
        assertThat(fleet.nodes.values.mapNotNull { it.ssh?.port }).containsOnly(2222)
        assertThat(fleet.placementIntent.gpuSpecific.getValue("jellyfin").preferredGpuModel).isEqualTo("t1000")
        assertThat(fleet.exposureIntent.publicAndLan)
            .containsExactlyInAnyOrder("bazarr", "jellyfin", "jellyseerr", "prowlarr", "qbittorrent", "radarr", "sonarr")
        assertThat(fleet.ingressIntent.kubernetesBackends.getValue("vault").port).isEqualTo(8200)
        assertThat(fleet.ingressIntent.kubernetesBackends.getValue("auth-api").namespace).isEqualTo("auth-system")
        assertThat(fleet.ingressIntent.kubernetesBackends.getValue("assistant-api").port).isEqualTo(8082)
        assertThat(fleet.ingressIntent.kubernetesBackends.getValue("stalwart").namespace).isEqualTo("mail-system")
        assertThat(fleet.ingressIntent.kubernetesBackends.getValue("stalwart").port).isEqualTo(8080)
        assertThat(fleet.ingressIntent.kubernetesBackends.getValue("bazarr").port).isEqualTo(6767)
        assertThat(fleet.ingressIntent.kubernetesBackends.getValue("jellyseerr").port).isEqualTo(5055)
        assertThat(fleet.ingressIntent.kubernetesBackends.getValue("prowlarr").port).isEqualTo(9696)
        assertThat(fleet.ingressIntent.kubernetesBackends.getValue("qbittorrent").port).isEqualTo(8080)
        assertThat(fleet.ingressIntent.kubernetesBackends).doesNotContainKey("headscale")
    }

    @Test
    fun `rejects active nodes without ssh connection details`() {
        val inventoryPath =
            tempInventory(
                """
                version: 1
                cluster:
                  name: personal-stack
                  public_domain: jorisjonkers.dev
                  kubernetes:
                    bootstrap_control_plane: frankfurt-contabo-1
                    api_server_endpoint: https://167.86.79.203:6443
                    control_plane_token_file: /var/lib/rancher/k3s/server/node-token
                    worker_join_token_file: /var/lib/personal-stack/secrets/k3s/agent-token
                sites:
                  frankfurt:
                    kind: vps
                    purpose: primary_cluster_site
                nodes:
                  frankfurt-contabo-1:
                    status: active
                    site: frankfurt
                    arch: amd64
                    target_roles:
                      - k3s-control-plane
                    capacity:
                      cpu_millicores: 1000
                      memory_mib: 1024
                    capabilities:
                      - tailscale
                service_intent:
                  kubernetes:
                    public_apps: []
                    internal_platform: []
                    home_media: []
                  host_native: {}
                placement_intent:
                  frankfurt_only: []
                  enschede_only: []
                  gpu_specific: {}
                exposure_intent:
                  public: []
                  public_and_lan: []
                  internal_only: []
                  lan_only: []
                """.trimIndent(),
            )

        assertThatThrownBy { loader.load(inventoryPath) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("active node frankfurt-contabo-1 must define ssh connection details")
    }

    @Test
    fun `rejects unknown site references`() {
        val inventoryPath =
            tempInventory(
                """
                version: 1
                cluster:
                  name: personal-stack
                  public_domain: jorisjonkers.dev
                  kubernetes:
                    bootstrap_control_plane: stray-node
                    api_server_endpoint: https://167.86.79.203:6443
                    control_plane_token_file: /var/lib/rancher/k3s/server/node-token
                    worker_join_token_file: /var/lib/personal-stack/secrets/k3s/agent-token
                sites:
                  frankfurt:
                    kind: vps
                    purpose: primary_cluster_site
                nodes:
                  stray-node:
                    status: planned
                    site: enschede
                    arch: arm64
                    target_roles:
                      - k3s-control-plane
                      - k3s-worker
                    capacity:
                      cpu_millicores: 1000
                      memory_mib: 1024
                    capabilities:
                      - tailscale
                service_intent:
                  kubernetes:
                    public_apps: []
                    internal_platform: []
                    home_media: []
                  host_native: {}
                placement_intent:
                  frankfurt_only: []
                  enschede_only: []
                  gpu_specific: {}
                exposure_intent:
                  public: []
                  public_and_lan: []
                  internal_only: []
                  lan_only: []
                """.trimIndent(),
            )

        assertThatThrownBy { loader.load(inventoryPath) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("node stray-node references unknown site enschede")
    }

    @Test
    fun `rejects externally exposed kubernetes services without ingress backends`() {
        val inventoryPath =
            tempInventory(
                """
                version: 1
                cluster:
                  name: personal-stack
                  public_domain: jorisjonkers.dev
                  kubernetes:
                    bootstrap_control_plane: frankfurt-contabo-1
                    api_server_endpoint: https://167.86.79.203:6443
                    control_plane_token_file: /var/lib/rancher/k3s/server/node-token
                    worker_join_token_file: /var/lib/personal-stack/secrets/k3s/agent-token
                sites:
                  frankfurt:
                    kind: vps
                    purpose: primary_cluster_site
                nodes:
                  frankfurt-contabo-1:
                    status: active
                    site: frankfurt
                    arch: amd64
                    ssh:
                      host: 167.86.79.203
                      user: deploy
                      port: 2222
                    target_roles:
                      - k3s-control-plane
                    capacity:
                      cpu_millicores: 1000
                      memory_mib: 1024
                    capabilities:
                      - tailscale
                service_intent:
                  kubernetes:
                    public_apps:
                      - app-ui
                    internal_platform: []
                    home_media: []
                  host_native: {}
                placement_intent:
                  frankfurt_only:
                    - app-ui
                  enschede_only: []
                  gpu_specific: {}
                exposure_intent:
                  public:
                    - app-ui
                  public_and_lan: []
                  internal_only: []
                  lan_only: []
                access_intent:
                  host_labels:
                    app-ui: root
                ingress_intent:
                  kubernetes_backends: {}
                """.trimIndent(),
            )

        assertThatThrownBy { loader.load(inventoryPath) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("externally exposed kubernetes service app-ui must declare an ingress backend")
    }

    private fun tempInventory(content: String) =
        createTempDirectory()
            .resolve("fleet.yaml")
            .also { it.writeText(content) }
}
