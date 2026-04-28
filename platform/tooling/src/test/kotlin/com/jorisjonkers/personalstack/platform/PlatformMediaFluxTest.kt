package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformMediaFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `production cluster kustomization includes media apps`() {
        val clusterKustomization =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomizations.yaml").toFile().readText()
        val appKustomization = repositoryRoot.resolve("platform/cluster/flux/apps/media/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/media/namespace.yaml").toFile().readText()

        assertThat(clusterKustomization)
            .contains("name: apps-media")
            .contains("path: ./platform/cluster/flux/apps/media")
        assertThat(appKustomization)
            .contains("- bazarr")
            .contains("- downloads")
            .contains("namespace.yaml")
            .contains("- jellyfin")
            .contains("- jellyseerr")
            .contains("- radarr")
            .contains("- sonarr")
        assertThat(namespace)
            .contains("kind: Namespace")
            .contains("name: media-system")
    }

    @Test
    fun `jellyfin prefers the t1000 gpu while staying on enschede nvidia utility hosts`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/media/jellyfin/kustomization.yaml").toFile().readText()
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/media/jellyfin/deployment.yaml").toFile().readText()

        assertThat(kustomization).contains("deployment.yaml")

        assertThat(manifest)
            .contains("kind: Deployment")
            .contains("name: jellyfin")
            .contains("namespace: media-system")
            .contains("jellyfin/jellyfin:10.11.8")
            .contains("personal-stack/site: enschede")
            .contains("personal-stack/role-utility-host:")
            .contains("personal-stack/capability-samba:")
            .contains("personal-stack/capability-nvidia:")
            .contains("personal-stack/gpu-model-t1000")
            .contains("personal-stack/gpu-model-gtx960m")
            .contains("path: /srv/media/Series")
            .contains("path: /srv/media/Films")
            .contains("path: /var/lib/personal-stack/media/jellyfin")
            .contains("mountPath: /media/Series")
            .contains("mountPath: /media/Films")
            .contains("mountPath: /config")
            .contains("containerPort: 8096")
            .contains("path: /health")
            .contains("nvidia.com/gpu: 1")
            .contains("kind: Service")
            .contains("port: 8096")
            .doesNotContain("path: /srv/media\n")
    }

    @Test
    fun `radarr and sonarr run on enschede utility hosts with shared media storage`() {
        val radarrManifest = repositoryRoot.resolve("platform/cluster/flux/apps/media/radarr/deployment.yaml").toFile().readText()
        val sonarrManifest = repositoryRoot.resolve("platform/cluster/flux/apps/media/sonarr/deployment.yaml").toFile().readText()

        assertThat(radarrManifest)
            .contains("name: radarr")
            .contains("namespace: media-system")
            .contains("linuxserver/radarr:latest")
            .contains("personal-stack/site: enschede")
            .contains("personal-stack/role-utility-host:")
            .contains("personal-stack/capability-samba:")
            .contains("path: /srv/media/Completed")
            .contains("path: /srv/media/Films")
            .contains("path: /var/lib/personal-stack/media/radarr")
            .contains("mountPath: /media/Completed")
            .contains("mountPath: /media/Films")
            .contains("mountPath: /config")
            .contains("path: /ping")
            .contains("containerPort: 7878")
            .contains("kind: Service")
            .contains("port: 7878")
            .doesNotContain("path: /srv/media\n")

        assertThat(sonarrManifest)
            .contains("name: sonarr")
            .contains("namespace: media-system")
            .contains("linuxserver/sonarr:latest")
            .contains("personal-stack/site: enschede")
            .contains("personal-stack/role-utility-host:")
            .contains("personal-stack/capability-samba:")
            .contains("path: /srv/media/Completed")
            .contains("path: /srv/media/Series")
            .contains("path: /var/lib/personal-stack/media/sonarr")
            .contains("mountPath: /media/Completed")
            .contains("mountPath: /media/Series")
            .contains("mountPath: /config")
            .contains("path: /ping")
            .contains("containerPort: 8989")
            .contains("kind: Service")
            .contains("port: 8989")
            .doesNotContain("path: /srv/media\n")
    }

    @Test
    fun `bazarr runs on enschede utility hosts with shared media storage`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/media/bazarr/kustomization.yaml").toFile().readText()
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/media/bazarr/deployment.yaml").toFile().readText()

        assertThat(kustomization).contains("deployment.yaml")

        assertThat(manifest)
            .contains("name: bazarr")
            .contains("namespace: media-system")
            .contains("linuxserver/bazarr:latest")
            .contains("personal-stack/site: enschede")
            .contains("personal-stack/role-utility-host:")
            .contains("personal-stack/capability-samba:")
            .contains("path: /srv/media/Series")
            .contains("path: /srv/media/Films")
            .contains("path: /var/lib/personal-stack/media/bazarr")
            .contains("mountPath: /media/Series")
            .contains("mountPath: /media/Films")
            .contains("mountPath: /config")
            .contains("containerPort: 6767")
            .contains("kind: Service")
            .contains("port: 6767")
            .doesNotContain("path: /srv/media\n")
    }

    @Test
    fun `downloads pod keeps vpn scoped services and vault delivered media credentials`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/media/downloads/kustomization.yaml").toFile().readText()
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/media/downloads/deployment.yaml").toFile().readText()

        assertThat(kustomization).contains("deployment.yaml")

        assertThat(manifest)
            .contains("kind: ServiceAccount")
            .contains("name: downloads")
            .contains("namespace: media-system")
            .contains("kind: Deployment")
            .contains("name: downloads")
            .contains("vault.hashicorp.com/agent-inject:")
            .contains("vault.hashicorp.com/agent-inject-token:")
            .contains("vault.hashicorp.com/role: downloads")
            .contains("secret/data/platform/media")
            .contains("pia.username")
            .contains("pia.password")
            .contains("pia.server_regions")
            .contains("/vault/secrets/gluetun.env")
            .contains("qmcgaw/gluetun:v3.40")
            .contains("exec /gluetun-entrypoint")
            .contains("linuxserver/qbittorrent:latest")
            .contains("linuxserver/prowlarr:latest")
            .contains("NET_ADMIN")
            .contains("NET_RAW")
            .contains("path: /srv/media/Downloading")
            .contains("path: /srv/media/Completed")
            .contains("path: /var/lib/personal-stack/media/qbittorrent")
            .contains("path: /var/lib/personal-stack/media/prowlarr")
            .contains("mountPath: /media/Downloading")
            .contains("mountPath: /media/Completed")
            .contains("name: tailscale-local-route")
            .contains("100.64.0.0/10")
            .contains("containerPort: 8080")
            .contains("containerPort: 9696")
            .contains("kind: Service")
            .contains("name: qbittorrent")
            .contains("name: prowlarr")
            .contains("port: 8080")
            .contains("port: 9696")
            .doesNotContain("path: /srv/media\n")
    }

    @Test
    fun `jellyseerr runs on enschede utility hosts with persisted config`() {
        val kustomization = repositoryRoot.resolve("platform/cluster/flux/apps/media/jellyseerr/kustomization.yaml").toFile().readText()
        val manifest = repositoryRoot.resolve("platform/cluster/flux/apps/media/jellyseerr/deployment.yaml").toFile().readText()

        assertThat(kustomization).contains("deployment.yaml")

        assertThat(manifest)
            .contains("name: jellyseerr")
            .contains("namespace: media-system")
            .contains("ghcr.io/seerr-team/seerr:v3.1.1")
            .contains("personal-stack/site: enschede")
            .contains("personal-stack/role-utility-host:")
            .contains("path: /srv/media/Series")
            .contains("path: /srv/media/Films")
            .contains("path: /var/lib/personal-stack/media/jellyseerr")
            .contains("mountPath: /media/Series")
            .contains("mountPath: /media/Films")
            .contains("readOnly: true")
            .contains("mountPath: /app/config")
            .contains("containerPort: 5055")
            .contains("path: /api/v1/settings/public")
            .contains("kind: Service")
            .contains("port: 5055")
            .doesNotContain("path: /srv/media\n")
    }

    @Test
    fun `vault bootstrap configures downloads media policy and kubernetes role`() {
        val bootstrapScript =
            repositoryRoot.resolve("platform/cluster/flux/apps/data/vault/bootstrap-auth.sh").toFile().readText()

        assertThat(bootstrapScript)
            .contains("vault policy write downloads")
            .contains("auth/kubernetes/role/downloads")
            .contains("secret/data/platform/media")
            .contains("bound_service_account_names=\"downloads\"")
            .contains("bound_service_account_namespaces=\"media-system\"")
    }
}
