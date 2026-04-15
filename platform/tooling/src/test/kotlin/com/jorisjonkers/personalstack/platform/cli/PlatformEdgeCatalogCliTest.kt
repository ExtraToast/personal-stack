package com.jorisjonkers.personalstack.platform.cli

import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PlatformEdgeCatalogCliTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `render-edge-catalog emits vault as public sso protected`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("render-edge-catalog")

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("name: \"vault\"")
            .contains("exposure: \"public\"")
            .contains("access: \"sso_protected\"")
            .contains("host: \"vault.jorisjonkers.dev\"")
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("name: \"app-ui\"")
            .contains("host: \"jorisjonkers.dev\"")
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("name: \"uptime-kuma\"")
            .contains("host: \"status.jorisjonkers.dev\"")
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("name: \"rabbitmq\"")
            .contains("exposure: \"public\"")
            .contains("access: \"sso_protected\"")
            .contains("host: \"rabbitmq.jorisjonkers.dev\"")
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("name: \"bazarr\"")
            .contains("exposure: \"public_and_lan\"")
            .contains("access: \"sso_protected\"")
            .contains("host: \"bazarr.jorisjonkers.dev\"")
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("name: \"prowlarr\"")
            .contains("exposure: \"public_and_lan\"")
            .contains("access: \"sso_protected\"")
            .contains("host: \"prowlarr.jorisjonkers.dev\"")
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("name: \"qbittorrent\"")
            .contains("exposure: \"public_and_lan\"")
            .contains("access: \"sso_protected\"")
            .contains("host: \"qbittorrent.jorisjonkers.dev\"")
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("name: \"postgres\"")
            .contains("exposure: \"internal_only\"")
            .contains("access: \"cluster_internal\"")
            .doesNotContain("test_host")
            .doesNotContain("jorisjonkers.test")
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }
}
