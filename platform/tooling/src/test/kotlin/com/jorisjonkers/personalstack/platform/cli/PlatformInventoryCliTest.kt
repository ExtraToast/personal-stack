package com.jorisjonkers.personalstack.platform.cli

import com.jorisjonkers.personalstack.platform.RepositoryRootLocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PlatformInventoryCliTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `show-host-env prints ssh and nix metadata for active nodes`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("show-host-env", "frankfurt-contabo-1")

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("NODE_NAME=frankfurt-contabo-1")
            .contains("NODE_STATUS=active")
            .contains("NIX_SYSTEM=x86_64-linux")
            .contains("SSH_HOST=167.86.79.203")
            .contains("SSH_USER=deploy")
            .contains("SSH_PORT=2222")
            .contains("HAS_SSH=true")
            .contains("IS_CONTROL_PLANE=true")
            .contains("IS_WORKER=true")
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }

    @Test
    fun `show-host-env prints steady state ssh metadata for install ready nodes`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("show-host-env", "enschede-pi-1")

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("NODE_NAME=enschede-pi-1")
            .contains("NODE_STATUS=install-ready")
            .contains("NIX_SYSTEM=aarch64-linux")
            .contains("HAS_SSH=true")
            .contains("SSH_HOST=enschede-pi-1")
            .contains("SSH_USER=deploy")
            .contains("SSH_PORT=2222")
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }

    @Test
    fun `show-install-host-env leaves ssh blank when install ready node has no bootstrap ssh yet`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("show-install-host-env", "enschede-pi-1")

        assertThat(exitCode).isEqualTo(0)
        assertThat(stdout.toString(StandardCharsets.UTF_8))
            .contains("NODE_NAME=enschede-pi-1")
            .contains("NODE_STATUS=install-ready")
            .contains("HAS_SSH=false")
            .contains("SSH_HOST=")
            .contains("SSH_USER=")
            .contains("SSH_PORT=")
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isBlank()
    }

    @Test
    fun `unknown host returns non-zero with a helpful error`() {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        val exitCode =
            PlatformInventoryCli(
                repositoryRoot = repositoryRoot,
                stdout = stdout.writer(StandardCharsets.UTF_8),
                stderr = stderr.writer(StandardCharsets.UTF_8),
            ).run("show-host-env", "missing-node")

        assertThat(exitCode).isEqualTo(1)
        assertThat(stdout.toString(StandardCharsets.UTF_8)).isBlank()
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("Unknown node: missing-node")
    }
}
