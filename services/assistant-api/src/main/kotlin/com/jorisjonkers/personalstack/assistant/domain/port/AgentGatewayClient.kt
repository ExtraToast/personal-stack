package com.jorisjonkers.personalstack.assistant.domain.port

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentKind

/**
 * Driven port: the HTTP/WS facade in front of an agent-gateway
 * running inside a workspace's runner Pod. Methods are the small
 * intersection of operations assistant-api actually needs — anything
 * more granular is invoked by the agent itself via its tooling,
 * not by this control plane.
 */
interface AgentGatewayClient {
    data class GatewayAgent(
        val id: String,
        val kind: WorkspaceAgentKind,
        val cwd: String,
    )

    fun spawnAgent(
        workspace: Workspace,
        kind: WorkspaceAgentKind,
        workspacePath: String? = null,
    ): GatewayAgent

    fun stopAgent(
        workspace: Workspace,
        gatewayAgentId: String,
    )

    fun sendInput(
        workspace: Workspace,
        gatewayAgentId: String,
        input: String,
        enter: Boolean = true,
    )

    fun capture(
        workspace: Workspace,
        gatewayAgentId: String,
    ): String

    fun clone(
        workspace: Workspace,
        repoUrl: String,
        branch: String? = null,
    ): String

    fun openPr(
        workspace: Workspace,
        repoDir: String,
        title: String,
        body: String,
        base: String = "main",
    ): String

    fun isReady(workspace: Workspace): Boolean
}
