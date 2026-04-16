package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlatformDeployScriptsTest {
    private val repositoryRoot = RepositoryRootLocator().locate()
    private val platformFlakeRef = "path:${repositoryRoot.resolve("platform")}"

    @TempDir
    lateinit var tempDir: Path

    private fun authorizedKeysFile(): String =
        tempDir.resolve("authorized-keys.nix")
            .also { Files.writeString(it, "[ ]\n") }
            .toAbsolutePath()
            .toString()

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
                listOf("frankfurt-contabo-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                        "PLATFORM_AUTHORIZED_KEYS_FILE" to authorizedKeysFile(),
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "--extra-experimental-features",
                "nix-command flakes",
                "run",
                "${platformFlakeRef}#nixos-anywhere",
                "--",
                "--flake",
                "${platformFlakeRef}#frankfurt-contabo-1",
                "--target-host",
                "deploy@167.86.79.203",
                "--ssh-port",
                "2222",
            )
    }

    @Test
    fun `install-host can target install ready nodes through bootstrap deploy ssh`() {
        val gradlewStub =
            tempDir.resolve("gradlew-install-ready").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=enschede-t1000-1
                NODE_STATUS=install-ready
                NODE_SITE=enschede
                NODE_ARCH=amd64
                NIX_SYSTEM=x86_64-linux
                HAS_SSH=true
                SSH_HOST=enschede-t1000-1
                SSH_USER=deploy
                SSH_PORT=2222
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-install-ready.log")
        val nixStub =
            tempDir.resolve("nix-install-ready-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/install/install-host.sh"),
                listOf("enschede-t1000-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                        "PLATFORM_AUTHORIZED_KEYS_FILE" to authorizedKeysFile(),
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "--extra-experimental-features",
                "nix-command flakes",
                "run",
                "${platformFlakeRef}#nixos-anywhere",
                "--",
                "--flake",
                "${platformFlakeRef}#enschede-t1000-1",
                "--target-host",
                "deploy@enschede-t1000-1",
                "--ssh-port",
                "2222",
            )
    }

    @Test
    fun `install-host forwards an explicit ssh identity file to nixos-anywhere`() {
        val gradlewStub =
            tempDir.resolve("gradlew-ssh-key").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=enschede-t1000-1
                NODE_STATUS=install-ready
                NODE_SITE=enschede
                NODE_ARCH=amd64
                NIX_SYSTEM=x86_64-linux
                HAS_SSH=true
                SSH_HOST=192.168.1.50
                SSH_USER=extratoast
                SSH_PORT=22
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-ssh-key.log")
        val nixStub =
            tempDir.resolve("nix-ssh-key-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                """.trimIndent(),
            )
        val sshKey = tempDir.resolve("ps-t1000")
        Files.writeString(sshKey, "private-key")

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/install/install-host.sh"),
                listOf("--ssh-key", sshKey.toAbsolutePath().toString(), "enschede-t1000-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                        "PLATFORM_AUTHORIZED_KEYS_FILE" to authorizedKeysFile(),
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "--extra-experimental-features",
                "nix-command flakes",
                "run",
                "${platformFlakeRef}#nixos-anywhere",
                "--",
                "--flake",
                "${platformFlakeRef}#enschede-t1000-1",
                "--target-host",
                "extratoast@192.168.1.50",
                "--ssh-port",
                "22",
                "-i",
                sshKey.toAbsolutePath().toString(),
            )
    }

    @Test
    fun `install-host forwards an explicit ssh password to nixos-anywhere`() {
        val gradlewStub =
            tempDir.resolve("gradlew-password").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=enschede-t1000-1
                NODE_STATUS=install-ready
                NODE_SITE=enschede
                NODE_ARCH=amd64
                NIX_SYSTEM=x86_64-linux
                HAS_SSH=true
                SSH_HOST=192.168.1.50
                SSH_USER=extratoast
                SSH_PORT=22
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-password.log")
        val sshPassLog = tempDir.resolve("nix-password-env.log")
        val nixStub =
            tempDir.resolve("nix-password-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                printf '%s' "${'$'}{SSHPASS:-}" > "${sshPassLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/install/install-host.sh"),
                listOf("--ssh-password", "bootstrap-secret", "enschede-t1000-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                        "PLATFORM_AUTHORIZED_KEYS_FILE" to authorizedKeysFile(),
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "--extra-experimental-features",
                "nix-command flakes",
                "run",
                "${platformFlakeRef}#nixos-anywhere",
                "--",
                "--flake",
                "${platformFlakeRef}#enschede-t1000-1",
                "--target-host",
                "extratoast@192.168.1.50",
                "--ssh-port",
                "22",
                "--env-password",
            )
        assertThat(Files.readString(sshPassLog)).isEqualTo("bootstrap-secret")
    }

    @Test
    fun `install-host can override inventory ssh for temporary installer sessions`() {
        val gradlewStub =
            tempDir.resolve("gradlew-installer-override").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=enschede-pi-1
                NODE_STATUS=install-ready
                NODE_SITE=enschede
                NODE_ARCH=arm64
                NIX_SYSTEM=aarch64-linux
                HAS_SSH=false
                HAS_BOOTSTRAP_SSH=true
                SSH_HOST=
                SSH_USER=
                SSH_PORT=
                BOOTSTRAP_SSH_HOST=192.168.0.132
                BOOTSTRAP_SSH_USER=deploy
                BOOTSTRAP_SSH_PORT=22
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-installer-override.log")
        val sshPassLog = tempDir.resolve("nix-installer-override-env.log")
        val nixStub =
            tempDir.resolve("nix-installer-override-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                printf '%s' "${'$'}{SSHPASS:-}" > "${sshPassLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/install/install-host.sh"),
                listOf("--ssh-password", "nixos-installer", "enschede-pi-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                        "PLATFORM_AUTHORIZED_KEYS_FILE" to authorizedKeysFile(),
                        "PLATFORM_INSTALL_SSH_HOST" to "192.168.0.140",
                        "PLATFORM_INSTALL_SSH_USER" to "nixos",
                        "PLATFORM_INSTALL_SSH_PORT" to "22",
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "--extra-experimental-features",
                "nix-command flakes",
                "run",
                "${platformFlakeRef}#nixos-anywhere",
                "--",
                "--flake",
                "${platformFlakeRef}#enschede-pi-1",
                "--target-host",
                "nixos@192.168.0.140",
                "--ssh-port",
                "22",
                "--env-password",
            )
        assertThat(Files.readString(sshPassLog)).isEqualTo("nixos-installer")
    }

    @Test
    fun `install-host builds on remote when the local and target platforms differ`() {
        val gradlewStub =
            tempDir.resolve("gradlew-remote-build").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=enschede-t1000-1
                NODE_STATUS=install-ready
                NODE_SITE=enschede
                NODE_ARCH=amd64
                NIX_SYSTEM=x86_64-linux
                HAS_SSH=true
                SSH_HOST=192.168.0.100
                SSH_USER=extratoat
                SSH_PORT=22
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-remote-build.log")
        val nixStub =
            tempDir.resolve("nix-remote-build-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/install/install-host.sh"),
                listOf("enschede-t1000-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                        "PLATFORM_AUTHORIZED_KEYS_FILE" to authorizedKeysFile(),
                        "PLATFORM_CURRENT_SYSTEM" to "aarch64-darwin",
                        "PLATFORM_INSTALL_BUILD_ON" to "auto",
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "--extra-experimental-features",
                "nix-command flakes",
                "run",
                "${platformFlakeRef}#nixos-anywhere",
                "--",
                "--flake",
                "${platformFlakeRef}#enschede-t1000-1",
                "--target-host",
                "extratoat@192.168.0.100",
                "--ssh-port",
                "22",
                "--build-on",
                "remote",
            )
    }

    @Test
    fun `install-host rejects install ready nodes without bootstrap ssh details`() {
        val gradlewStub =
            tempDir.resolve("gradlew-no-ssh").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=enschede-pi-1
                NODE_STATUS=install-ready
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
                listOf("enschede-pi-1"),
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
    fun `install-host rejects missing authorized keys file for key only hosts`() {
        val gradlewStub =
            tempDir.resolve("gradlew-missing-authorized-keys").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                NODE_NAME=enschede-t1000-1
                NODE_STATUS=install-ready
                NODE_SITE=enschede
                NODE_ARCH=amd64
                NIX_SYSTEM=x86_64-linux
                HAS_SSH=true
                SSH_HOST=enschede-t1000-1
                SSH_USER=deploy
                SSH_PORT=2222
                EOF
                """.trimIndent(),
            )
        val nixLog = tempDir.resolve("nix-missing-authorized-keys.log")
        val nixStub =
            tempDir.resolve("nix-missing-authorized-keys-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/install/install-host.sh"),
                listOf("enschede-t1000-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                        "PLATFORM_AUTHORIZED_KEYS_FILE" to tempDir.resolve("missing-authorized-keys.nix").toString(),
                    ),
            )

        assertThat(result.exitCode).isEqualTo(1)
        assertThat(result.stderr).contains("create it from platform/nix/authorized-keys.nix.example")
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
                listOf("frankfurt-contabo-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readAllLines(nixLog))
            .containsExactly(
                "--extra-experimental-features",
                "nix-command flakes",
                "run",
                "github:serokell/deploy-rs",
                "--",
                "${platformFlakeRef}#frankfurt-contabo-1",
            )
    }

    @Test
    fun `deploy-host exports flake features for nested nix invocations`() {
        val gradlewStub =
            tempDir.resolve("gradlew-deploy-nix-config").writeExecutable(
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
        val nixLog = tempDir.resolve("nix-deploy-nix-config.log")
        val nixConfigLog = tempDir.resolve("nix-config.log")
        val nixStub =
            tempDir.resolve("nix-deploy-nix-config-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                printf '%s\n' "$@" > "${nixLog.toAbsolutePath()}"
                printf '%s' "${'$'}{NIX_CONFIG:-}" > "${nixConfigLog.toAbsolutePath()}"
                """.trimIndent(),
            )

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/deploy/deploy-host.sh"),
                listOf("frankfurt-contabo-1"),
                environment =
                    mapOf(
                        "PLATFORM_GRADLEW" to gradlewStub,
                        "PLATFORM_NIX" to nixStub,
                        "NIX_CONFIG" to "substituters = https://cache.nixos.org",
                    ),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readString(nixConfigLog))
            .isEqualTo(
                """
                substituters = https://cache.nixos.org
                experimental-features = nix-command flakes
                """.trimIndent(),
            )
    }

    private fun runScript(
        script: Path,
        arguments: List<String>,
        environment: Map<String, String>,
    ): ProcessResult {
        val process =
            ProcessBuilder(listOf(script.toAbsolutePath().toString()) + arguments)
                .directory(repositoryRoot.toFile())
                .apply {
                    environment().putAll(environment)
                    environment().putIfAbsent("PLATFORM_INSTALL_BUILD_ON", "local")
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
