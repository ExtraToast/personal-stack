package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlatformPiImageBuildScriptTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val platformFlakeRef = "path:${repositoryRoot.resolve("platform")}"

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `build-pi-image targets the host specific sd image output`() {
        val gradlewStub =
            tempDir.resolve("gradlew-pi-image").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=enschede-pi-1
                NODE_STATUS=install-ready
                NODE_SITE=enschede
                NODE_ARCH=arm64
                NIX_SYSTEM=aarch64-linux
                HAS_SSH=true
                SSH_HOST=enschede-pi-1
                SSH_USER=deploy
                SSH_PORT=2222
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-pi-image.log")
        val nixStub =
            tempDir.resolve("nix-pi-image-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/build/build-pi-image.sh"),
                listOf("enschede-pi-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                        "PLATFORM_CURRENT_SYSTEM" to "aarch64-linux",
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "--extra-experimental-features",
                "nix-command flakes",
                "build",
                "${platformFlakeRef}#piSdImages.enschede-pi-1",
                "--out-link",
                "result-enschede-pi-1-sd-image",
                "--print-build-logs",
            )
    }

    @Test
    fun `build-pi-image rejects non arm hosts`() {
        val gradlewStub =
            tempDir.resolve("gradlew-non-pi-image").writeExecutable(
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
        val nixStub =
            tempDir.resolve("nix-non-pi-image-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                echo should-not-run >&2
                exit 99
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/build/build-pi-image.sh"),
                listOf("frankfurt-contabo-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                    ),
            )

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.stderr).contains("not an arm64 Raspberry Pi image target")
    }

    private fun runScript(
        script: Path,
        arguments: List<String>,
        environment: Map<String, String>,
    ): PiImageProcessResult {
        val process =
            ProcessBuilder(listOf(script.toAbsolutePath().toString()) + arguments)
                .directory(repositoryRoot.toFile())
                .apply { environment().putAll(environment) }
                .start()

        return PiImageProcessResult(
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

private data class PiImageProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
