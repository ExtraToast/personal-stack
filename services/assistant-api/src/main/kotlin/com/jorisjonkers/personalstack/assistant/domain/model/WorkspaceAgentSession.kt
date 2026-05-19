package com.jorisjonkers.personalstack.assistant.domain.model

import java.time.Instant

enum class WorkspaceAgentSessionStatus { STARTING, RUNNING, STOPPED, FAILED }

/**
 * One agent process inside a workspace's runner Pod. The
 * `gatewayAgentId` is what the agent-gateway hands back when we POST
 * /agents — the assistant-api always addresses gateway resources by
 * that short id, never by our own UUID, because the gateway is the
 * source of truth for "is this process actually alive".
 */
data class WorkspaceAgentSession(
    val id: WorkspaceAgentSessionId,
    val workspaceId: WorkspaceId,
    val kind: WorkspaceAgentKind,
    val gatewayAgentId: String?,
    val status: WorkspaceAgentSessionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun bindGatewayAgent(gatewayAgentId: String): WorkspaceAgentSession =
        copy(
            gatewayAgentId = gatewayAgentId,
            status = WorkspaceAgentSessionStatus.RUNNING,
            updatedAt = Instant.now(),
        )

    fun markStopped(): WorkspaceAgentSession =
        copy(status = WorkspaceAgentSessionStatus.STOPPED, updatedAt = Instant.now())

    fun markFailed(): WorkspaceAgentSession =
        copy(status = WorkspaceAgentSessionStatus.FAILED, updatedAt = Instant.now())
}
