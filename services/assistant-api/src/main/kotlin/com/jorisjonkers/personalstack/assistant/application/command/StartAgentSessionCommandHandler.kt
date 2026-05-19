package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSession
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionStatus
import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceAgentSessionRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class StartAgentSessionCommandHandler(
    private val workspaces: WorkspaceRepository,
    private val sessions: WorkspaceAgentSessionRepository,
    private val gateway: AgentGatewayClient,
) : CommandHandler<StartAgentSessionCommand> {
    @Transactional
    override fun handle(command: StartAgentSessionCommand) {
        val workspace =
            workspaces.findById(command.workspaceId)
                ?: error("workspace not found: ${command.workspaceId}")

        val now = Instant.now()
        val session =
            WorkspaceAgentSession(
                id = command.sessionId,
                workspaceId = workspace.id,
                kind = command.kind,
                gatewayAgentId = null,
                status = WorkspaceAgentSessionStatus.STARTING,
                createdAt = now,
                updatedAt = now,
            )
        sessions.save(session)

        val gatewayAgent = gateway.spawnAgent(workspace, command.kind)
        sessions.save(session.bindGatewayAgent(gatewayAgent.id))
    }
}
