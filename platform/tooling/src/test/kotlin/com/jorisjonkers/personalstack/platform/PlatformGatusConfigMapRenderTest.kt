package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlatformGatusConfigMapRenderTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `render-gatus-config-configmap writes a flux-ready configmap manifest`() {
        val gradlewStub =
            tempDir.resolve("gradlew-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: gatus-endpoints
                  namespace: observability
                data:
                  endpoints.yaml: |
                    ---
                    endpoints:
                    - name: "grafana"
                      group: "public-apps"
                      url: "https://grafana.jorisjonkers.dev/api/health"
                EOF
                """.trimIndent(),
            )
        val outputPath = tempDir.resolve("gatus-endpoints-configmap.yaml")

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/render/render-gatus-config-configmap.sh"),
                outputPath.toString(),
                environment = mapOf("PLATFORM_GRADLEW" to gradlewStub),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readString(outputPath))
            .contains("kind: ConfigMap")
            .contains("name: gatus-endpoints")
            .contains("namespace: observability")
            .contains("endpoints.yaml: |")
            .contains("name: \"grafana\"")
        assertThat(result.stderr).isBlank()
    }

    private fun runScript(
        script: Path,
        outputPath: String,
        environment: Map<String, String>,
    ): GatusRenderProcessResult {
        val process =
            ProcessBuilder(script.toAbsolutePath().toString(), outputPath)
                .directory(repositoryRoot.toFile())
                .apply {
                    environment().putAll(environment)
                }.start()

        return GatusRenderProcessResult(
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

private data class GatusRenderProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
