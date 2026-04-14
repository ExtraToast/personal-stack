package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlatformEdgeConfigMapRenderTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `render-edge-catalog-configmap writes a flux-ready configmap manifest`() {
        val gradlewStub =
            tempDir.resolve("gradlew-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                ---
                cluster: "personal-stack"
                services:
                - name: "vault"
                  exposure: "public"
                  access: "sso_protected"
                EOF
                """.trimIndent(),
            )
        val outputPath = tempDir.resolve("edge-catalog-configmap.yaml")

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/render/render-edge-catalog-configmap.sh"),
                outputPath.toString(),
                environment = mapOf("PLATFORM_GRADLEW" to gradlewStub),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readString(outputPath))
            .contains("kind: ConfigMap")
            .contains("name: platform-edge-catalog")
            .contains("namespace: edge-system")
            .contains("edge-catalog.yaml: |")
            .contains("cluster: \"personal-stack\"")
            .contains("name: \"vault\"")
        assertThat(result.stderr).isBlank()
    }

    private fun runScript(
        script: Path,
        outputPath: String,
        environment: Map<String, String>,
    ): EdgeRenderProcessResult {
        val process =
            ProcessBuilder(script.toAbsolutePath().toString(), outputPath)
                .directory(repositoryRoot.toFile())
                .apply {
                    environment().putAll(environment)
                }.start()

        return EdgeRenderProcessResult(
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

private data class EdgeRenderProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
