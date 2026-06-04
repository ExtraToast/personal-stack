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

    @Test
    fun `agent runner wires github app token into gh github mcp and git push`() {
        val dockerfile =
            repositoryRoot
                .resolve("services/agent-runner/Dockerfile")
                .toFile()
                .readText()
        val entrypoint =
            repositoryRoot
                .resolve("services/agent-runner/entrypoint.sh")
                .toFile()
                .readText()
        val agentGatewayConfig =
            repositoryRoot
                .resolve("services/agent-gateway/src/main/resources/application.yml")
                .toFile()
                .readText()
        val githubMcpWrapper =
            repositoryRoot
                .resolve("services/agent-runner/gh-mcp-wrapper.sh")
                .toFile()
                .readText()
        val githubTokenHelper =
            repositoryRoot
                .resolve("services/agent-runner/agent-github-token.sh")
                .toFile()
                .readText()
        val mcpConfigMap =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/agents/mcp/agents-mcp-servers-configmap.yaml")
                .toFile()
                .readText()

        assertThat(dockerfile)
            .contains("agent-github-token.sh /usr/local/bin/agent-github-token")
            .contains("gh-app-token-wrapper.sh /usr/local/bin/gh")
            .contains("git-credential-agent-gh-app.sh /usr/local/bin/git-credential-agent-gh-app")
            .contains("gh-mcp-wrapper.sh /usr/local/bin/gh-mcp-wrapper")

        assertThat(entrypoint)
            .contains("credential.helper agent-gh-app")
            .contains("credential.useHttpPath true")
            .contains("url.https://github.com/.insteadOf git@github.com:")
            .contains("url.https://github.com/.insteadOf ssh://git@github.com/")

        assertThat(agentGatewayConfig)
            .contains("--dangerously-bypass-approvals-and-sandbox")
            .contains("--dangerously-bypass-hook-trust")

        assertThat(githubMcpWrapper)
            .contains("GITHUB_MCP_TOKEN_RETRIES:-4")
            .contains("GITHUB_MCP_TOKEN_RETRY_SLEEP_SECONDS:-3")
            .contains("GITHUB_APP_TOKEN_URL")
            .contains("GITHUB_APP_TOKEN_BEARER")
            .contains("GITHUB_MCP_TOOLSETS:-repos,pull_requests,issues,actions,git")
            .contains("GITHUB_MCP_EXCLUDE_TOOLS:-create_repository,fork_repository")
            .contains("--toolsets")
            .contains("--exclude-tools")

        assertThat(githubTokenHelper)
            .contains("WORKSPACE_ROOT=\"\${WORKSPACE_ROOT:-/workspace}\"")
            .contains("git -C \"\$WORKSPACE_ROOT\" remote get-url origin")
            .contains("--arg repoUrl \"\$repo_url\"")

        assertThat(mcpConfigMap)
            .contains("[mcp_servers.github]")
            .contains("command = \"gh-mcp-wrapper\"")
            .contains("startup_timeout_sec = 60")
    }

    @Test
    fun `kb installer cronjob refreshes claude and codex homes`() {
        val cronjob =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/agents/kb-install/cronjob.yaml")
                .toFile()
                .readText()

        assertThat(cronjob)
            .contains("name: CLAUDE_CONFIG_DIR")
            .contains("value: /home/agent/.claude")
            .contains("name: CODEX_HOME")
            .contains("value: /home/agent/.codex")
            .contains("| bash -s -- --agent all")
            .contains("name: claude-credentials")
            .contains("mountPath: /home/agent/.claude")
            .contains("claimName: claude-credentials")
            .contains("name: codex-credentials")
            .contains("mountPath: /home/agent/.codex")
            .contains("claimName: codex-credentials")
    }
}
