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

    /**
     * Result of the gateway's `/git/verify` probe. `read` proves the
     * staged deploy key can `git ls-remote`; `write` is a
     * non-destructive throwaway-ref push/delete against an existing
     * commit. `detail` carries the gateway's human-readable message.
     */
    data class AccessVerification(
        val read: Boolean,
        val write: Boolean,
        val detail: String,
    )

    /**
     * Ask the standing agent-gateway to verify deploy-key access to
     * [repoUrl] on [branch] (HEAD when null). Returns null when no
     * gateway base URL is configured or the call is inconclusive —
     * callers degrade gracefully rather than fail hard.
     */
    fun verifyAccess(
        repoUrl: String,
        branch: String?,
    ): AccessVerification?

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
