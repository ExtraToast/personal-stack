package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlatformEdgeRouteConfigMapRenderTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `render-edge-route-catalog-configmap writes a flux-ready configmap manifest`() {
        val gradlewStub =
            tempDir.resolve("gradlew-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: platform-edge-route-catalog
                  namespace: edge-system
                data:
                  edge-route-catalog.yaml: |
                    ---
                    cluster: "personal-stack"
                    routes:
                    - name: "vault"
                      service: "vault"
                      production_host: "vault.jorisjonkers.dev"
                      test_host: "vault.jorisjonkers.test"
                      access: "sso_protected"
                EOF
                """.trimIndent(),
            )
        val outputPath = tempDir.resolve("edge-route-catalog-configmap.yaml")

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/render/render-edge-route-catalog-configmap.sh"),
                outputPath.toString(),
                environment = mapOf("PLATFORM_GRADLEW" to gradlewStub),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readString(outputPath))
            .contains("kind: ConfigMap")
            .contains("name: platform-edge-route-catalog")
            .contains("namespace: edge-system")
            .contains("edge-route-catalog.yaml: |")
            .contains("cluster: \"personal-stack\"")
            .contains("name: \"vault\"")
        assertThat(result.stderr).isBlank()
    }

    private fun runScript(
        script: Path,
        outputPath: String,
        environment: Map<String, String>,
    ): EdgeRouteRenderProcessResult {
        val process =
            ProcessBuilder(script.toAbsolutePath().toString(), outputPath)
                .directory(repositoryRoot.toFile())
                .apply {
                    environment().putAll(environment)
                }.start()

        return EdgeRouteRenderProcessResult(
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

private data class EdgeRouteRenderProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
