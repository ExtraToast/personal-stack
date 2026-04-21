package com.jorisjonkers.personalstack.platform.cli

import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PlatformGatusConfigMapCliTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `render-gatus-configmap matches the committed flux artifact`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("render-gatus-configmap")

        assertThat(exitCode).isEqualTo(0)
        val expectedArtifact =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/observability/gatus/gatus-endpoints-configmap.yaml")
                .toFile()
                .readText()
        assertThat(stdout.toString(StandardCharsets.UTF_8)).isEqualTo(expectedArtifact)
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }

    @Test
    fun `render-gatus-configmap emits internal probes for sso-protected services and includes tcp checks`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("render-gatus-configmap")

        assertThat(exitCode).isEqualTo(0)
        val output = stdout.toString(StandardCharsets.UTF_8)
        assertThat(output)
            .contains("kind: ConfigMap")
            .contains("name: gatus-endpoints")
            .contains("namespace: observability")
            .contains("endpoints.yaml: |")
            .contains("\"assistant-api\"")
            .contains("http://assistant-api.assistant-system.svc.cluster.local:8082/api/actuator/health")
            .contains("tcp://rabbitmq.data-system.svc.cluster.local:5672")
            .contains("\"[CONNECTED] == true\"")
            .contains("\"gatus\"")
            .contains("\"public-apps\"")
            .contains("https://jorisjonkers.dev/")
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }
}
