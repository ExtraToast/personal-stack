package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlatformDeployScriptsTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `install-host uses nixos-anywhere with ssh metadata from inventory cli`() {
        val gradlewStub =
            tempDir.resolve("gradlew-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=frankfurt-contabo-1
                NODE_STATUS=active
                NODE_SITE=frankfurt
                NODE_ARCH=amd64
                NIX_SYSTEM=x86_64-linux
                HAS_SSH=true
                SSH_HOST=167.86.79.203
                SSH_USER=deploy
                SSH_PORT=2222
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-install.log")
        val nixStub =
            tempDir.resolve("nix-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/install/install-host.sh"),
                "frankfurt-contabo-1",
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "run",
                ".#nixos-anywhere",
                "--",
                "--flake",
                ".#frankfurt-contabo-1",
                "--target-host",
                "deploy@167.86.79.203",
                "--ssh-port",
                "2222",
            )
    }

    @Test
    fun `install-host rejects nodes without ssh details`() {
        val gradlewStub =
            tempDir.resolve("gradlew-no-ssh").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=enschede-pi-1
                NODE_STATUS=planned
                NODE_SITE=enschede
                NODE_ARCH=arm64
                NIX_SYSTEM=aarch64-linux
                HAS_SSH=false
                SSH_HOST=
                SSH_USER=
                SSH_PORT=
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-not-called.log")
        val nixStub =
            tempDir.resolve("nix-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/install/install-host.sh"),
                "enschede-pi-1",
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                    ),
            )

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.stderr).contains("does not define SSH connection details")
        assertThat(Files.exists(nixLog)).isFalse()
    }

    @Test
    fun `deploy-host uses deploy-rs against the requested node`() {
        val gradlewStub =
            tempDir.resolve("gradlew-deploy").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=frankfurt-contabo-1
                NODE_STATUS=active
                NODE_SITE=frankfurt
                NODE_ARCH=amd64
                NIX_SYSTEM=x86_64-linux
                HAS_SSH=true
                SSH_HOST=167.86.79.203
                SSH_USER=deploy
                SSH_PORT=2222
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-deploy.log")
        val nixStub =
            tempDir.resolve("nix-deploy-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/deploy/deploy-host.sh"),
                "frankfurt-contabo-1",
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "run",
                "github:serokell/deploy-rs",
                "--",
                ".#frankfurt-contabo-1",
            )
    }

    private fun runScript(
        script: Path,
        nodeName: String,
        environment: Map<String, String>,
    ): ProcessResult {
        val process =
            ProcessBuilder(script.toAbsolutePath().toString(), nodeName)
                .directory(repositoryRoot.toFile())
                .apply {
                    environment().putAll(environment)
                }.start()

        return ProcessResult(
            exitCode = process.waitFor(),
            stdout = process.inputStream.readAllBytes().decodeToString(),
            stderr = process.errorStream.readAllBytes().decodeToString(),
        )
    }

    private fun Path.writeExecutable(contents: String): String {
        Files.writeString(this, contents)
        toFile().setExecutable(true)
        return toAbsolutePath().toString()
    }
}

private data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
