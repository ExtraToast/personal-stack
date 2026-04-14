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
            .contains("name: vault")
            .contains("exposure: public")
            .contains("access: sso_protected")
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("name: postgres")
            .contains("exposure: internal_only")
            .contains("access: cluster_internal")
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }
}
