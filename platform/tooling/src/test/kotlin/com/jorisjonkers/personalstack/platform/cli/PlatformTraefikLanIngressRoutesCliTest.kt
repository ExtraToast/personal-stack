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
            .contains("name: jellyfin-lan")
            .contains("name: radarr-lan")
            .contains("name: sonarr-lan")
            .contains("kubernetes.io/ingress.class: traefik-lan")
            .contains("match: 'Host(`jellyfin.jorisjonkers.dev`)'")
            .contains("match: 'Host(`radarr.jorisjonkers.dev`)'")
            .contains("match: 'Host(`sonarr.jorisjonkers.dev`)'")
            .doesNotContain("name: vault-lan")
            .doesNotContain("external-dns.alpha.kubernetes.io/target")

        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }
}
