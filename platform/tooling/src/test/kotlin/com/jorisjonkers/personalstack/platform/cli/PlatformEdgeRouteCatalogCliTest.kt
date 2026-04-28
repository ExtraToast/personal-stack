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
            .contains("host: \"jorisjonkers.dev\"")

        assertThat(output)
            .contains("name: \"auth-api-well-known\"")
            .contains("service: \"auth-api\"")
            .contains("host: \"auth.jorisjonkers.dev\"")
            .contains("path_prefixes:")
            .contains("- \"/.well-known/\"")

        assertThat(output)
            .contains("name: \"assistant-api\"")
            .contains("service: \"assistant-api\"")
            .contains("host: \"assistant.jorisjonkers.dev\"")
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
            .contains("host: \"vault.jorisjonkers.dev\"")
            .contains("access: \"sso_protected\"")

        assertThat(output)
            .contains("name: \"rabbitmq\"")
            .contains("service: \"rabbitmq\"")
            .contains("host: \"rabbitmq.jorisjonkers.dev\"")
            .contains("access: \"sso_protected\"")
            .contains("name: \"stalwart\"")
            .contains("service: \"stalwart\"")
            .contains("host: \"stalwart.jorisjonkers.dev\"")
            .contains("name: \"bazarr\"")
            .contains("service: \"bazarr\"")
            .contains("host: \"bazarr.jorisjonkers.dev\"")
            .contains("name: \"jellyseerr\"")
            .contains("service: \"jellyseerr\"")
            .contains("host: \"jellyseerr.jorisjonkers.dev\"")
            .contains("access: \"direct\"")
            .contains("name: \"prowlarr\"")
            .contains("service: \"prowlarr\"")
            .contains("host: \"prowlarr.jorisjonkers.dev\"")
            .contains("name: \"qbittorrent\"")
            .contains("service: \"qbittorrent\"")
            .contains("host: \"qbittorrent.jorisjonkers.dev\"")
            .contains("name: \"wolf\"")
            .contains("service: \"wolf\"")
            .contains("host: \"wolf.jorisjonkers.dev\"")
            .contains("access: \"sso_protected\"")
            .doesNotContain("test_host")
            .doesNotContain("jorisjonkers.test")

        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }
}
