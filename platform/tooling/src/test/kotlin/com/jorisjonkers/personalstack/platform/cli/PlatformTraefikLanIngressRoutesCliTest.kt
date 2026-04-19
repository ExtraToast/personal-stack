package com.jorisjonkers.personalstack.platform.cli

import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PlatformTraefikLanIngressRoutesCliTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `render-traefik-lan-ingressroutes matches the committed flux artifact`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("render-traefik-lan-ingressroutes")

        assertThat(exitCode).isEqualTo(0)
        val expectedArtifact = repositoryRoot.resolve("platform/cluster/flux/apps/edge/traefik-lan-ingressroutes.yaml").toFile().readText()
        assertThat(stdout.toString(StandardCharsets.UTF_8)).isEqualTo(expectedArtifact)
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }

    @Test
    fun `render-traefik-lan-ingressroutes emits only dual exposed home media services`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("render-traefik-lan-ingressroutes")

        assertThat(exitCode).isEqualTo(0)
        val output = stdout.toString(StandardCharsets.UTF_8)

        assertThat(output)
            // The LAN renderer mirrors the media-stack path split. LAN
            // routes are always access=direct (accessOverride), so the
            // split here doesn't gate anything with forward-auth; it
            // just keeps the route names aligned with the public
            // renderer so every service has a consistent `-api` sibling.
            .contains("name: bazarr-lan")
            .contains("name: bazarr-api-lan")
            .contains("name: jellyfin-lan")
            .contains("name: jellyseerr-lan")
            .contains("name: prowlarr-lan")
            .contains("name: prowlarr-api-lan")
            .contains("name: qbittorrent-lan")
            .contains("name: qbittorrent-api-lan")
            .contains("name: radarr-lan")
            .contains("name: radarr-api-lan")
            .contains("name: sonarr-lan")
            .contains("name: sonarr-api-lan")
            .contains("kubernetes.io/ingress.class: traefik-lan")
            .contains("match: 'Host(`bazarr.jorisjonkers.dev`) && !PathPrefix(`/api/`)'")
            .contains("match: 'Host(`bazarr.jorisjonkers.dev`) && PathPrefix(`/api/`)'")
            .contains("match: 'Host(`jellyfin.jorisjonkers.dev`)'")
            .contains("match: 'Host(`jellyseerr.jorisjonkers.dev`)'")
            .contains("match: 'Host(`prowlarr.jorisjonkers.dev`) && !PathPrefix(`/api/`)'")
            .contains("match: 'Host(`prowlarr.jorisjonkers.dev`) && PathPrefix(`/api/`)'")
            .contains("match: 'Host(`qbittorrent.jorisjonkers.dev`) && !PathPrefix(`/api/`)'")
            .contains("match: 'Host(`qbittorrent.jorisjonkers.dev`) && PathPrefix(`/api/`)'")
            .contains("match: 'Host(`radarr.jorisjonkers.dev`) && !PathPrefix(`/api/`)'")
            .contains("match: 'Host(`radarr.jorisjonkers.dev`) && PathPrefix(`/api/`)'")
            .contains("match: 'Host(`sonarr.jorisjonkers.dev`) && !PathPrefix(`/api/`)'")
            .contains("match: 'Host(`sonarr.jorisjonkers.dev`) && PathPrefix(`/api/`)'")
            .doesNotContain("name: forward-auth")
            .doesNotContain("name: vault-lan")
            .doesNotContain("external-dns.alpha.kubernetes.io/target")

        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }
}
