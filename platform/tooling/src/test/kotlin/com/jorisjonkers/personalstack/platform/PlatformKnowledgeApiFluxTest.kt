package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Locks in the Phase 4b knowledge-api wiring:
 *
 *  1. The VSO `knowledge-api-mcp-tokens` Secret exists and projects
 *     every Vault field under `secret/data/knowledge-system/mcp-bearer`
 *     as-is. No `transformation.templates` block so adding a new
 *     device only requires a `vault kv patch`, not a manifest edit.
 *  2. The Deployment `envFrom`s that Secret with the
 *     `KNOWLEDGE_MCP_TOKENS_` prefix — that is what makes Spring's
 *     `@ConfigurationProperties("knowledge.mcp")` see each Vault field
 *     as a token under `tokens.<name>`.
 *  3. The rendered ingress splits `kb.jorisjonkers.dev` into two
 *     routes: the bearer-only `/mcp` and `/mcp/...` paths skip
 *     forward-auth (Spring's `McpBearerFilter` is the gate); the rest
 *     stays behind forward-auth. Forward-auth on /mcp would 401 every
 *     CLI / SDK request that does not carry an SSO cookie.
 */
class PlatformKnowledgeApiFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `mcp bearer tokens VSO secret projects every field with no transformation`() {
        val vss =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/knowledge/knowledge-api/vault-secrets.yaml")
                .toFile()
                .readText()

        assertThat(vss)
            .contains("name: knowledge-api-mcp-tokens")
            .contains("namespace: knowledge-system")
            .contains("path: knowledge-system/mcp-bearer")
            // No templates — relying on VSO's default field projection
            // so a `vault kv patch` adds new tokens without a manifest
            // edit. If a future change adds a `transformation.templates`
            // block, the operator-add-a-device flow regresses to
            // "edit the YAML, render, PR, merge".
            .doesNotContain("transformation:")
        // Rollout restart targets the deployment so a key rotation
        // takes effect within one reconcile cycle.
        assertThat(vss)
            .contains("rolloutRestartTargets:")
            .contains("name: knowledge-api")
    }

    @Test
    fun `deployment envFrom mounts the mcp tokens secret with the KNOWLEDGE_MCP_TOKENS_ prefix`() {
        val deployment =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/knowledge/knowledge-api/deployment.yaml")
                .toFile()
                .readText()

        assertThat(deployment)
            .contains("name: knowledge-api-mcp-tokens")
            .contains("prefix: KNOWLEDGE_MCP_TOKENS_")
    }

    @Test
    fun `kb host renders a bearer-only mcp route alongside the sso-protected default route`() {
        val ingressRoutes =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/edge/traefik-ingressroutes.yaml")
                .toFile()
                .readText()

        // Default route: the host minus /mcp + /mcp/*, with forward-auth.
        val defaultRoute =
            Regex(
                """name: knowledge-api\n.*?(?=\n---|\z)""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(ingressRoutes)?.value
                ?: error("no IngressRoute named 'knowledge-api' rendered")
        assertThat(defaultRoute)
            .describedAs("kb.* host (minus /mcp) must be SSO-protected")
            .contains("name: forward-auth")
            .contains("!PathPrefix(`/mcp/`)")
            .contains("!Path(`/mcp`)")

        // Bearer-only route: /mcp and /mcp/* with no middleware.
        val mcpRoute =
            Regex(
                """name: knowledge-api-mcp\n.*?(?=\n---|\z)""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(ingressRoutes)?.value
                ?: error("no IngressRoute named 'knowledge-api-mcp' rendered")
        assertThat(mcpRoute)
            .describedAs(
                "kb.*/mcp must NOT be SSO-protected — forward-auth 401s every bearer-only CLI " +
                    "request. McpBearerFilter inside the Spring service is the gate.",
            ).doesNotContain("name: forward-auth")
            .contains("PathPrefix(`/mcp/`)")
            .contains("Path(`/mcp`)")
    }
}
