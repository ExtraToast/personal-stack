package com.jorisjonkers.personalstack.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression test for the browser-Obsidian + forward-auth WebSocket
 * collision.
 *
 * The linuxserver/obsidian image streams the Obsidian UI through
 * Selkies, which opens a WebRTC data channel over a `/ws` WebSocket
 * for input + framebuffer signalling. With forward-auth in the
 * ingress chain the SPA initial HTML loads (because the browser
 * presents the SSO cookie set during the redirect), but the
 * subsequent `/ws` upgrade is treated by forward-auth as a fresh
 * unauthenticated request and gets 302'd to the auth-ui login.
 * Browsers can't follow a redirect during a WebSocket handshake, so
 * the session stalls at a blank Selkies frame.
 *
 * The mitigation is to put Selkies in `BASIC_AUTH_USER` /
 * `BASIC_AUTH_PASSWORD` mode — every endpoint including `/ws`
 * requires HTTP Basic Auth, which browsers re-send automatically on
 * each same-origin request — and drop the forward-auth middleware so
 * Basic Auth becomes the single gate. This test pins both halves of
 * the contract.
 */
class PlatformObsidianFluxTest {
    private val repositoryRoot = RepositoryRootLocator().locate()

    @Test
    fun `obsidian deployment sources selkies basic auth from the obsidian-selkies VSO secret`() {
        val deployment =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/knowledge/obsidian/deployment.yaml")
                .toFile()
                .readText()
        val vaultSecrets =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/knowledge/obsidian/vault-secrets.yaml")
                .toFile()
                .readText()

        assertThat(vaultSecrets)
            .contains("name: obsidian-selkies")
            .contains("knowledge-system/obsidian-selkies")
            .contains("BASIC_AUTH_USER")
            .contains("BASIC_AUTH_PASSWORD")
        assertThat(deployment)
            .contains("image: linuxserver/obsidian:latest")
            .contains("secretRef:")
            .contains("name: obsidian-selkies")
    }

    @Test
    fun `obsidian is not sso_protected so forward-auth does not 302 the selkies websocket`() {
        val fleet = repositoryRoot.resolve("platform/inventory/fleet.yaml").toFile().readText()

        val ssoBlock =
            Regex("""sso_protected:\s*\n((?:\s+#.*\n|\s+- [^\n]+\n)+)""").find(fleet)?.groupValues?.get(1)
                ?: error("could not find access_intent.sso_protected block in fleet.yaml")

        assertThat(ssoBlock)
            .describedAs(
                "obsidian must NOT be in access_intent.sso_protected — forward-auth 302s the Selkies " +
                    "WebSocket upgrade and the browser cannot follow a redirect mid-handshake, so the " +
                    "session stalls at a blank screen. The pod authenticates via Selkies BASIC_AUTH " +
                    "instead (creds from the obsidian-selkies VSO Secret).",
            ).doesNotContain("- obsidian\n")
    }

    @Test
    fun `gatus expects 401 from obsidian because selkies basic auth gates every endpoint`() {
        val gatus =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/observability/gatus/gatus-endpoints-configmap.yaml")
                .toFile()
                .readText()

        // The rendered Gatus endpoint for `obsidian` must accept 401
        // as healthy — an unauthenticated probe to a BASIC_AUTH-fronted
        // Selkies endpoint *should* 401. A condition of `[STATUS] == 200`
        // would flip the dashboard red while the service is fine.
        val obsidianBlock =
            Regex(
                """name: "obsidian"\s*\n(?:.*\n){0,8}""",
            ).find(gatus)?.value
                ?: error("no gatus endpoint named 'obsidian' rendered")
        assertThat(obsidianBlock)
            .contains("[STATUS] == 401")
            .doesNotContain("[STATUS] == 200")
    }

    @Test
    fun `obsidian ingressroute does not attach the forward-auth middleware`() {
        val ingressRoutes =
            repositoryRoot
                .resolve("platform/cluster/flux/apps/edge/traefik-ingressroutes.yaml")
                .toFile()
                .readText()

        val obsidianRoute =
            Regex(
                """(apiVersion: traefik\.io[^-]*?name: obsidian\n.*?(?=\n---|\z))""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(ingressRoutes)?.value
                ?: error("no IngressRoute named 'obsidian' in the rendered traefik-ingressroutes.yaml")

        assertThat(obsidianRoute)
            .describedAs(
                "The rendered obsidian IngressRoute must not include the forward-auth middleware. " +
                    "If this re-appears, run the renderer after the fleet.yaml edit that removed " +
                    "obsidian from access_intent.sso_protected.",
            ).doesNotContain("name: forward-auth")
    }
}
