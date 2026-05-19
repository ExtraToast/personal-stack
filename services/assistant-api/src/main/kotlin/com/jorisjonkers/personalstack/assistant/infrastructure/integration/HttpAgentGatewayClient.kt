package com.jorisjonkers.personalstack.assistant.infrastructure.integration

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentKind
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Thin REST adapter for the agent-gateway sidecar. Each method is
 * one HTTP call; the gateway is the sole authority over what those
 * verbs actually mean, so this client deliberately holds no logic of
 * its own beyond URI building and response mapping.
 */
@Component
class HttpAgentGatewayClient(
    private val restClient: RestClient,
) : AgentGatewayClient {
    private data class GatewayAgentDto(
        val id: String,
        val kind: WorkspaceAgentKind,
        val cwd: String,
    )

    private data class SpawnRequest(val kind: WorkspaceAgentKind, val workspacePath: String? = null)

    private data class SendRequest(val input: String, val enter: Boolean)

    private data class CloneRequest(val repoUrl: String, val branch: String? = null, val intoDir: String? = null)

    private data class OpenPrRequest(val repoDir: String, val title: String, val body: String, val base: String = "main")

    private data class GitResponse(val ok: Boolean, val output: String)

    private fun endpoint(workspace: Workspace): String =
        workspace.gatewayEndpoint ?: error("workspace ${workspace.id} not yet provisioned with a gateway endpoint")

    override fun spawnAgent(
        workspace: Workspace,
        kind: WorkspaceAgentKind,
        workspacePath: String?,
    ): AgentGatewayClient.GatewayAgent {
        val dto =
            restClient
                .post()
                .uri("${endpoint(workspace)}/agents")
                .body(SpawnRequest(kind, workspacePath))
                .retrieve()
                .body(GatewayAgentDto::class.java)
                ?: error("empty response from gateway")
        return AgentGatewayClient.GatewayAgent(id = dto.id, kind = dto.kind, cwd = dto.cwd)
    }

    override fun stopAgent(workspace: Workspace, gatewayAgentId: String) {
        restClient
            .delete()
            .uri("${endpoint(workspace)}/agents/$gatewayAgentId")
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError) { _, _ -> /* idempotent — ignore 404 */ }
            .toBodilessEntity()
    }

    override fun sendInput(workspace: Workspace, gatewayAgentId: String, input: String, enter: Boolean) {
        restClient
            .post()
            .uri("${endpoint(workspace)}/agents/$gatewayAgentId/send")
            .body(SendRequest(input, enter))
            .retrieve()
            .toBodilessEntity()
    }

    override fun capture(workspace: Workspace, gatewayAgentId: String): String {
        @Suppress("UNCHECKED_CAST")
        val body =
            restClient
                .get()
                .uri("${endpoint(workspace)}/agents/$gatewayAgentId/capture")
                .retrieve()
                .body(Map::class.java) as Map<String, String>
        return body["text"] ?: ""
    }

    override fun clone(workspace: Workspace, repoUrl: String, branch: String?): String {
        val resp =
            restClient
                .post()
                .uri("${endpoint(workspace)}/git/clone")
                .body(CloneRequest(repoUrl = repoUrl, branch = branch))
                .retrieve()
                .body(GitResponse::class.java)
                ?: error("empty response from gateway")
        return resp.output
    }

    override fun openPr(workspace: Workspace, repoDir: String, title: String, body: String, base: String): String {
        val resp =
            restClient
                .post()
                .uri("${endpoint(workspace)}/git/open-pr")
                .body(OpenPrRequest(repoDir = repoDir, title = title, body = body, base = base))
                .retrieve()
                .body(GitResponse::class.java)
                ?: error("empty response from gateway")
        return resp.output
    }

    override fun isReady(workspace: Workspace): Boolean =
        runCatching {
            restClient.get().uri("${endpoint(workspace)}/healthz").retrieve().toBodilessEntity()
            true
        }.getOrElse { false }
}
