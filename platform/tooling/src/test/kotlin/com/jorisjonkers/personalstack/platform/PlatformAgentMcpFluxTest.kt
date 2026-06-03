package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformAgentMcpFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `kubernetes mcp server uses current read only http image contract`() {
        val manifest =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/agents/mcp/kubernetes-mcp-server/deployment.yaml")
                .toFile()
                .readText()

        assertThat(manifest)
            .contains("quay.io/containers/kubernetes_mcp_server:v0.0.62")
            .contains("- --port=8080")
            .contains("- --read-only")
            .contains("path: /healthz")
            .doesNotContain("ghcr.io/manusa/kubernetes-mcp-server")
            .doesNotContain("path: /mcp")
    }

    @Test
    fun `runner egress allows assistant api token minting for github mcp`() {
        val manifest =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/agents/network-policy/runner-egress.yaml")
                .toFile()
                .readText()

        assertThat(manifest)
            .contains("kubernetes.io/metadata.name: assistant-system")
            .contains("app.kubernetes.io/name")
            .contains("- assistant-api")
            .contains("- assistant-api-ws")
            .contains("port: 8082")
    }
}
