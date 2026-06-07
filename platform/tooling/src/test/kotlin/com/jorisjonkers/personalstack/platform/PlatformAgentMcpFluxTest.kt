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
    fun `agents namespace explicitly admits docker socket runner pods`() {
        val namespace =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/agents/namespace.yaml")
                .toFile()
                .readText()

        assertThat(namespace)
            .contains("mount /var/run/docker.sock")
            .contains("host-equivalent access")
            .contains("pod-security.kubernetes.io/enforce: privileged")
            .contains("pod-security.kubernetes.io/audit: restricted")
            .contains("pod-security.kubernetes.io/warn: restricted")
    }

    @Test
    fun `runner egress allows only docker node high ports for testcontainers callbacks`() {
        val manifest =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/agents/network-policy/runner-egress.yaml")
                .toFile()
                .readText()

        assertThat(manifest)
            .contains("TESTCONTAINERS_HOST_OVERRIDE")
            .contains("cidr: 100.89.41.92/32")
            .contains("port: 32768")
            .contains("endPort: 65535")
    }

    @Test
    fun `agent runner docker socket node capability and gid are pinned in platform config`() {
        val inventory = repositoryRoot.resolve("platform/inventory/fleet.yaml").toFile().readText()
        val host =
            repositoryRoot
                .resolve("platform/nix/hosts/enschede-gtx-960m-1/default.nix")
                .toFile()
                .readText()
        val appConfig =
            repositoryRoot
                .resolve("services/assistant-api/src/main/resources/application.yml")
                .toFile()
                .readText()
        val assistantApiDeployment =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/stateless/assistant-api/deployment.yaml")
                .toFile()
                .readText()
        val assistantApiWsDeployment =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/stateless/assistant-api/deployment-enschede-ws.yaml")
                .toFile()
                .readText()

        assertThat(inventory).contains("- docker-socket")
        assertThat(host)
            .contains("\"personal-stack/capability-docker-socket\" = \"true\"")
            .contains("--node-ip=100.89.41.92")
            .contains("networking.firewall.interfaces.\"cni0\".allowedTCPPortRanges")
            .contains("to = 65535;")
            .contains("users.groups.docker.gid = 131;")
        assertThat(appConfig)
            .contains("docker-socket-supplemental-groups: \${AGENT_RUNTIME_DOCKER_SOCKET_SUPPLEMENTAL_GROUPS:131}")
            .contains(
                "'[personal-stack/capability-docker-socket]': " +
                    "\${AGENT_RUNTIME_DOCKER_SOCKET_CAPABILITY:true}",
            )
        assertThat(assistantApiDeployment)
            .contains("name: AGENT_RUNTIME_DOCKER_SOCKET_SUPPLEMENTAL_GROUPS")
            .contains("value: '131'")
        assertThat(assistantApiWsDeployment)
            .contains("name: AGENT_RUNTIME_DOCKER_SOCKET_SUPPLEMENTAL_GROUPS")
            .contains("value: '131'")
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
            .contains("docker-ce-cli")
            .contains("docker-buildx-plugin")
            .contains("docker-compose-plugin")
            .contains("docker --version >/dev/null")
            .contains("docker compose version >/dev/null")
            .contains("agent-github-token.sh /usr/local/bin/agent-github-token")
            .contains("gh-app-token-wrapper.sh /usr/local/bin/gh")
            .contains("git-credential-agent-gh-app.sh /usr/local/bin/git-credential-agent-gh-app")
            .contains("gh-mcp-wrapper.sh /usr/local/bin/gh-mcp-wrapper")
            .contains("ARG SERENA_AGENT_VERSION=1.5.3")
            .contains("uv tool install -p 3.13 \"serena-agent==${'$'}{SERENA_AGENT_VERSION}\"")
            .contains("serena --help >/dev/null")

        assertThat(entrypoint)
            .contains("credential.helper agent-gh-app")
            .contains("credential.useHttpPath true")
            .contains("git config --global --get-all safe.directory")
            .contains("git config --global --add safe.directory \"\$WORKSPACE_ROOT\"")
            .contains("url.https://github.com/.insteadOf git@github.com:")
            .contains("url.https://github.com/.insteadOf ssh://git@github.com/")
            // Multi-repo: clone every REPO_URLS entry and bound the credential
            // helper to the workspace's repos via REPO_ALLOW.
            .contains("REPO_URLS")
            .contains("clone_repo_into_workspace \"${'$'}REPO_URL\"")
            .contains("_repo_target=\"${'$'}{WORKSPACE_ROOT}/${'$'}{_repo_name}\"")
            .contains("export REPO_ALLOW")
            .contains("register_repo_trust")

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
            .contains("--arg repoUrl \"\$REPO_URL_RESOLVED\"")
            // Per-repo minting: the credential helper passes the requested repo.
            .contains("AGENT_GITHUB_REPO_URL")

        assertThat(mcpConfigMap)
            .contains("[mcp_servers.github]")
            .contains("command = \"gh-mcp-wrapper\"")
            .contains("startup_timeout_sec = 60")
    }

    @Test
    fun `agent runner mcp profiles keep default tool count bounded`() {
        val entrypoint =
            repositoryRoot
                .resolve("services/agent-runner/entrypoint.sh")
                .toFile()
                .readText()
        val appConfig =
            repositoryRoot
                .resolve("services/assistant-api/src/main/resources/application.yml")
                .toFile()
                .readText()
        val orchestrator =
            repositoryRoot
                .resolve(
                    "services/assistant-api/src/main/kotlin/com/jorisjonkers/personalstack/assistant/" +
                        "infrastructure/k8s/Fabric8AgentRunnerOrchestrator.kt",
                )
                .toFile()
                .readText()
        val mcpConfigMap =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/agents/mcp/agents-mcp-servers-configmap.yaml")
                .toFile()
                .readText()

        assertThat(appConfig)
            .contains("default-mcp-profile: \${AGENT_RUNTIME_DEFAULT_MCP_PROFILE:minimal}")
        assertThat(orchestrator)
            .contains("withName(\"AGENT_MCP_PROFILE\")")
            .contains("props.defaultMcpProfile")
            .contains("DOCKER_HOST")
            .contains("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE")
            .contains("TESTCONTAINERS_HOST_OVERRIDE")
            .contains("withNewHostPath()")
            .contains("withType(\"Socket\")")
            .contains("withSupplementalGroups(podSupplementalGroups())")
        assertThat(entrypoint)
            .contains("AGENT_MCP_PROFILE=\"\${AGENT_MCP_PROFILE:-minimal}\"")
            .contains("claude-mcp-servers.\${AGENT_MCP_PROFILE}.json")
            .contains("codex-mcp-servers.\${AGENT_MCP_PROFILE}.toml")
            .contains("unknown AGENT_MCP_PROFILE")

        assertThat(mcpConfigMap)
            .contains("--context=claude-code")
            .contains("--context=codex")
            .contains("no-memories")
            .contains("--open-web-dashboard")

        assertClaudeProfile(
            mcpConfigMap,
            "minimal",
            expected = listOf("\"knowledge\"", "\"github\""),
            forbidden = listOf("\"context7\"", "\"vuetify\"", "\"playwright\"", "\"kubernetes\""),
            maxServers = 2,
        )
        assertClaudeProfile(
            mcpConfigMap,
            "frontend",
            expected = listOf("\"knowledge\"", "\"github\"", "\"context7\"", "\"vuetify\"", "\"playwright\""),
            forbidden = listOf("\"kubernetes\""),
            maxServers = 5,
        )
        assertClaudeProfile(
            mcpConfigMap,
            "cluster",
            expected = listOf("\"knowledge\"", "\"github\"", "\"kubernetes\""),
            forbidden = listOf("\"context7\"", "\"vuetify\"", "\"playwright\""),
            maxServers = 3,
        )
        assertClaudeProfile(
            mcpConfigMap,
            "code-intel",
            expected = listOf("\"knowledge\"", "\"github\"", "\"serena\""),
            forbidden = listOf("\"context7\"", "\"vuetify\"", "\"playwright\"", "\"kubernetes\""),
            maxServers = 3,
        )
        assertClaudeProfile(
            mcpConfigMap,
            "full-diagnostic",
            expected =
                listOf(
                    "\"knowledge\"",
                    "\"github\"",
                    "\"context7\"",
                    "\"vuetify\"",
                    "\"playwright\"",
                    "\"kubernetes\"",
                    "\"serena\"",
                ),
            forbidden = emptyList(),
            maxServers = 7,
        )

        assertCodexProfile(
            mcpConfigMap,
            "minimal",
            expected = listOf("[mcp_servers.knowledge]", "[mcp_servers.github]"),
            forbidden =
                listOf(
                    "[mcp_servers.context7]",
                    "[mcp_servers.vuetify]",
                    "[mcp_servers.playwright]",
                    "[mcp_servers.kubernetes]",
                ),
            maxServers = 2,
        )
        assertCodexProfile(
            mcpConfigMap,
            "frontend",
            expected =
                listOf(
                    "[mcp_servers.knowledge]",
                    "[mcp_servers.github]",
                    "[mcp_servers.context7]",
                    "[mcp_servers.vuetify]",
                    "[mcp_servers.playwright]",
                ),
            forbidden = listOf("[mcp_servers.kubernetes]"),
            maxServers = 5,
        )
        assertCodexProfile(
            mcpConfigMap,
            "cluster",
            expected = listOf("[mcp_servers.knowledge]", "[mcp_servers.github]", "[mcp_servers.kubernetes]"),
            forbidden =
                listOf(
                    "[mcp_servers.context7]",
                    "[mcp_servers.vuetify]",
                    "[mcp_servers.playwright]",
                ),
            maxServers = 3,
        )
        assertCodexProfile(
            mcpConfigMap,
            "code-intel",
            expected = listOf("[mcp_servers.knowledge]", "[mcp_servers.github]", "[mcp_servers.serena]"),
            forbidden =
                listOf(
                    "[mcp_servers.context7]",
                    "[mcp_servers.vuetify]",
                    "[mcp_servers.playwright]",
                    "[mcp_servers.kubernetes]",
                ),
            maxServers = 3,
        )
        assertCodexProfile(
            mcpConfigMap,
            "full-diagnostic",
            expected =
                listOf(
                    "[mcp_servers.knowledge]",
                    "[mcp_servers.github]",
                    "[mcp_servers.context7]",
                    "[mcp_servers.vuetify]",
                    "[mcp_servers.playwright]",
                    "[mcp_servers.kubernetes]",
                    "[mcp_servers.serena]",
                ),
            forbidden = emptyList(),
            maxServers = 7,
        )
    }

    @Test
    fun `agent runner mcp profile names stay synchronized across api runner and configmap`() {
        val agentRuntimePropertiesPath =
            "services/assistant-api/src/main/kotlin/com/jorisjonkers/personalstack/assistant/" +
                "config/AgentRuntimeProperties.kt"
        val mcpConfigMapPath = "platform/cluster/flux/apps/agents/mcp/agents-mcp-servers-configmap.yaml"
        val agentRuntimeProperties = repositoryRoot.resolve(agentRuntimePropertiesPath).toFile().readText()
        val entrypoint = repositoryRoot.resolve("services/agent-runner/entrypoint.sh").toFile().readText()
        val mcpConfigMap = repositoryRoot.resolve(mcpConfigMapPath).toFile().readText()

        val assistantProfiles = assistantValidatedProfiles(agentRuntimeProperties)

        assertThat(runnerAcceptedProfiles(entrypoint))
            .describedAs("services/agent-runner/entrypoint.sh AGENT_MCP_PROFILE allow-list")
            .containsExactlyElementsOf(assistantProfiles)
        assertThat(configMapProfiles(mcpConfigMap, prefix = "claude-mcp-servers", extension = "json"))
            .describedAs("Claude MCP profile ConfigMap keys")
            .containsExactlyElementsOf(assistantProfiles)
        assertThat(configMapProfiles(mcpConfigMap, prefix = "codex-mcp-servers", extension = "toml"))
            .describedAs("Codex MCP profile ConfigMap keys")
            .containsExactlyElementsOf(assistantProfiles)
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

    private fun assertClaudeProfile(
        manifest: String,
        profile: String,
        expected: List<String>,
        forbidden: List<String>,
        maxServers: Int,
    ) {
        val block = configMapBlock(manifest, "claude-mcp-servers.$profile.json")
        expected.forEach { assertThat(block).contains(it) }
        forbidden.forEach { assertThat(block).doesNotContain(it) }
        assertThat(Regex("\"type\"\\s*:").findAll(block).count()).isLessThanOrEqualTo(maxServers)
    }

    private fun assertCodexProfile(
        manifest: String,
        profile: String,
        expected: List<String>,
        forbidden: List<String>,
        maxServers: Int,
    ) {
        val block = configMapBlock(manifest, "codex-mcp-servers.$profile.toml")
        expected.forEach { assertThat(block).contains(it) }
        forbidden.forEach { assertThat(block).doesNotContain(it) }
        assertThat(Regex("\\[mcp_servers\\.").findAll(block).count()).isLessThanOrEqualTo(maxServers)
    }

    private fun assistantValidatedProfiles(source: String): Set<String> {
        val block =
            Regex("""VALID_MCP_PROFILES:\s*Set<String>\s*=\s*setOf\((.*?)\)""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.get(1)
                ?: error("AgentRuntimeProperties.VALID_MCP_PROFILES setOf(...) block not found")
        return quotedValues(block)
    }

    private fun runnerAcceptedProfiles(entrypoint: String): Set<String> {
        val profiles =
            Regex("""case "\${'$'}AGENT_MCP_PROFILE" in\s+([-A-Za-z0-9_.|]+)\) ;;""")
                .find(entrypoint)
                ?.groupValues
                ?.get(1)
                ?: error("entrypoint AGENT_MCP_PROFILE case allow-list not found")
        return profiles.split("|").toCollection(linkedSetOf())
    }

    private fun configMapProfiles(
        manifest: String,
        prefix: String,
        extension: String,
    ): Set<String> =
        Regex("""(?m)^  $prefix\.([-A-Za-z0-9_.]+)\.$extension: \|$""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toCollection(linkedSetOf())

    private fun quotedValues(block: String): Set<String> =
        Regex(""""([^"]+)"""")
            .findAll(block)
            .map { it.groupValues[1] }
            .toCollection(linkedSetOf())

    private fun configMapBlock(
        manifest: String,
        key: String,
    ): String {
        val marker = "  $key: |"
        val start = manifest.indexOf(marker)
        assertThat(start).describedAs("ConfigMap key $key").isGreaterThanOrEqualTo(0)
        val rest = manifest.substring(start + marker.length)
        val nextKey = Regex("\n  (?:#|[A-Za-z0-9_.-]+:)").find(rest)?.range?.first ?: -1
        return if (nextKey == -1) rest else rest.substring(0, nextKey)
    }
}
