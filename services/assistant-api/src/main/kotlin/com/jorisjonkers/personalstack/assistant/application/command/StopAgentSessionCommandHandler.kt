package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.domain.port.AgentGatewayClient
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceAgentSessionRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StopAgentSessionCommandHandler(
    private val workspaces: WorkspaceRepository,
    private val sessions: WorkspaceAgentSessionRepository,
    private val gateway: AgentGatewayClient,
) : CommandHandler<StopAgentSessionCommand, Unit> {
    @Transactional
    override fun handle(command: StopAgentSessionCommand) {
        val session = sessions.findById(command.sessionId) ?: return
        val workspace =
            workspaces.findById(session.workspaceId)
                ?: error("workspace ${session.workspaceId} missing for session ${session.id}")
        val gatewayId = session.gatewayAgentId
        if (gatewayId != null) {
            runCatching { gateway.stopAgent(workspace, gatewayId) }
        }
        sessions.save(session.markStopped())
    }
}
