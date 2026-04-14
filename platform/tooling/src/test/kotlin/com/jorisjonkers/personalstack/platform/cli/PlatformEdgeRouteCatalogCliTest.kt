package com.jorisjonkers.personalstack.platform.cli

import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PlatformEdgeRouteCatalogCliTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `render-edge-route-catalog emits shared-host and sso route rules`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("render-edge-route-catalog")

        assertThat(exitCode).isEqualTo(0)
        val output = stdout.toString(StandardCharsets.UTF_8)

        assertThat(output)
            .contains("name: \"app-ui\"")
            .contains("service: \"app-ui\"")
            .contains("production_host: \"jorisjonkers.dev\"")
            .contains("test_host: \"jorisjonkers.test\"")

        assertThat(output)
            .contains("name: \"auth-api-well-known\"")
            .contains("service: \"auth-api\"")
            .contains("production_host: \"auth.jorisjonkers.dev\"")
            .contains("path_prefixes:")
            .contains("- \"/.well-known/\"")

        assertThat(output)
            .contains("name: \"assistant-api\"")
            .contains("service: \"assistant-api\"")
            .contains("production_host: \"assistant.jorisjonkers.dev\"")
            .contains("access: \"sso_protected\"")
            .contains("- \"/api/\"")

        assertThat(output)
            .contains("name: \"assistant-api-health\"")
            .contains("service: \"assistant-api\"")
            .contains("access: \"direct\"")
            .contains("- \"/api/v1/health\"")
            .contains("- \"/api/actuator/health\"")
            .contains("- \"/api/actuator/health/\"")

        assertThat(output)
            .contains("name: \"vault\"")
            .contains("service: \"vault\"")
            .contains("production_host: \"vault.jorisjonkers.dev\"")
            .contains("access: \"sso_protected\"")

        assertThat(output)
            .contains("name: \"rabbitmq\"")
            .contains("service: \"rabbitmq\"")
            .contains("production_host: \"rabbitmq.jorisjonkers.dev\"")
            .contains("access: \"sso_protected\"")

        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }
}
