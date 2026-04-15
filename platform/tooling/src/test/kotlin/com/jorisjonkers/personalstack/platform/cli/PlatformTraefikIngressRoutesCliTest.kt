package com.jorisjonkers.personalstack.platform.cli

import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PlatformTraefikIngressRoutesCliTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `render-traefik-ingressroutes matches the committed flux artifact`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("render-traefik-ingressroutes")

        assertThat(exitCode).isEqualTo(0)
        val expectedArtifact = repositoryRoot.resolve("platform/cluster/flux/apps/edge/traefik-ingressroutes.yaml").toFile().readText()
        assertThat(stdout.toString(StandardCharsets.UTF_8)).isEqualTo(expectedArtifact)
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }

    @Test
    fun `render-traefik-ingressroutes emits forward auth and health split routes`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("render-traefik-ingressroutes")

        assertThat(exitCode).isEqualTo(0)
        val output = stdout.toString(StandardCharsets.UTF_8)

        assertThat(output)
            .contains("kind: IngressRoute")
            .contains("name: assistant-api")
            .contains("external-dns.alpha.kubernetes.io/target: ingress.jorisjonkers.dev")
            .contains("external-dns.alpha.kubernetes.io/cloudflare-proxied:")
            .contains("kubernetes.io/ingress.class: traefik-public")
            .contains("match: 'Host(`assistant.jorisjonkers.dev`) && PathPrefix(`/api/`) && !PathPrefix(`/api/actuator/health/`) && !Path(`/api/actuator/health`) && !Path(`/api/v1/health`)'")
            .contains("name: forward-auth")
            .contains("namespace: edge-system")
            .contains("port: 8082")

        assertThat(output)
            .contains("name: assistant-api-health")
            .contains("match: 'Host(`assistant.jorisjonkers.dev`) && (PathPrefix(`/api/actuator/health/`) || Path(`/api/actuator/health`) || Path(`/api/v1/health`))'")

        assertThat(output)
            .contains("name: auth-api-well-known")
            .contains("match: 'Host(`auth.jorisjonkers.dev`) && PathPrefix(`/.well-known/`)'")

        assertThat(output)
            .contains("name: vault")
            .contains("match: 'Host(`vault.jorisjonkers.dev`)'")
            .contains("namespace: data-system")
            .contains("port: 8200")
            .contains("name: stalwart")
            .contains("match: 'Host(`stalwart.jorisjonkers.dev`)'")
            .contains("namespace: mail-system")
            .contains("port: 8080")
            .contains("name: bazarr")
            .contains("match: 'Host(`bazarr.jorisjonkers.dev`)'")
            .contains("port: 6767")
            .contains("name: prowlarr")
            .contains("match: 'Host(`prowlarr.jorisjonkers.dev`)'")
            .contains("port: 9696")
            .contains("name: qbittorrent")
            .contains("match: 'Host(`qbittorrent.jorisjonkers.dev`)'")
            .contains("port: 8080")
            .doesNotContain("jorisjonkers.test")

        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }
}
