package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformMediaFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `production cluster kustomization includes media apps`() {
        val clusterKustomization =
            repositoryRoot.resolve("platform/cluster/flux/clusters/production/kustomization.yaml").toFile().readText()
        val appKustomization = repositoryRoot.resolve("platform/cluster/flux/apps/media/kustomization.yaml").toFile().readText()
        val namespace = repositoryRoot.resolve("platform/cluster/flux/apps/media/namespace.yaml").toFile().readText()

        assertThat(clusterKustomization).contains("- ../../apps/media")
        assertThat(appKustomization)
            .contains("namespace.yaml")
            .contains("- jellyfin")
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
            .contains("path: /srv/media")
            .contains("path: /var/lib/personal-stack/media/jellyfin")
            .contains("mountPath: /media")
            .contains("mountPath: /config")
            .contains("containerPort: 8096")
            .contains("path: /health")
            .contains("kind: Service")
            .contains("port: 8096")
    }

    @Test
    fun `radarr and sonarr run on enschede utility hosts with shared media storage`() {
        val radarrManifest = repositoryRoot.resolve("platform/cluster/flux/apps/media/radarr/deployment.yaml").toFile().readText()
        val sonarrManifest = repositoryRoot.resolve("platform/cluster/flux/apps/media/sonarr/deployment.yaml").toFile().readText()

        assertThat(radarrManifest)
            .contains("name: radarr")
            .contains("namespace: media-system")
            .contains("linuxserver/radarr:5.21.1")
            .contains("personal-stack/site: enschede")
            .contains("personal-stack/role-utility-host:")
            .contains("personal-stack/capability-samba:")
            .contains("path: /srv/media")
            .contains("path: /var/lib/personal-stack/media/radarr")
            .contains("mountPath: /media")
            .contains("mountPath: /config")
            .contains("path: /ping")
            .contains("containerPort: 7878")
            .contains("kind: Service")
            .contains("port: 7878")

        assertThat(sonarrManifest)
            .contains("name: sonarr")
            .contains("namespace: media-system")
            .contains("linuxserver/sonarr:4.0.14")
            .contains("personal-stack/site: enschede")
            .contains("personal-stack/role-utility-host:")
            .contains("personal-stack/capability-samba:")
            .contains("path: /srv/media")
            .contains("path: /var/lib/personal-stack/media/sonarr")
            .contains("mountPath: /media")
            .contains("mountPath: /config")
            .contains("path: /ping")
            .contains("containerPort: 8989")
            .contains("kind: Service")
            .contains("port: 8989")
    }
}
