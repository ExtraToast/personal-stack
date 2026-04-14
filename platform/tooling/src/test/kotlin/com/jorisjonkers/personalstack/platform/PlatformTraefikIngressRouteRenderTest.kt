package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlatformTraefikIngressRouteRenderTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `render-traefik-ingressroutes writes a flux-ready ingressroute manifest`() {
        val gradlewStub =
            tempDir.resolve("gradlew-stub").writeExecutable(
                """
                #!/usr/bin/env bash
                cat <<'EOF'
                apiVersion: traefik.io/v1alpha1
                kind: IngressRoute
                metadata:
                  name: vault
                  namespace: edge-system
                spec:
                  entryPoints:
                    - websecure
                  routes:
                    - kind: Rule
                      match: "Host(`vault.jorisjonkers.dev`)"
                      middlewares:
                        - name: forward-auth
                          namespace: edge-system
                      services:
                        - name: vault
                          namespace: data-system
                          port: 8200
                  tls: {}
                EOF
                """.trimIndent(),
            )
        val outputPath = tempDir.resolve("traefik-ingressroutes.yaml")

        val result =
            runScript(
                repositoryRoot.resolve("platform/scripts/render/render-traefik-ingressroutes.sh"),
                outputPath.toString(),
                environment = mapOf("PLATFORM_GRADLEW" to gradlewStub),
            )

        assertThat(result.exitCode).isEqualTo(0)
        assertThat(Files.readString(outputPath))
            .contains("kind: IngressRoute")
            .contains("name: vault")
            .contains("namespace: edge-system")
            .contains("middlewares:")
            .contains("name: forward-auth")
            .contains("port: 8200")
        assertThat(result.stderr).isBlank()
    }

    private fun runScript(
        script: Path,
        outputPath: String,
        environment: Map<String, String>,
    ): TraefikRouteRenderProcessResult {
        val process =
            ProcessBuilder(script.toAbsolutePath().toString(), outputPath)
                .directory(repositoryRoot.toFile())
                .apply {
                    environment().putAll(environment)
                }.start()

        return TraefikRouteRenderProcessResult(
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

private data class TraefikRouteRenderProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)
