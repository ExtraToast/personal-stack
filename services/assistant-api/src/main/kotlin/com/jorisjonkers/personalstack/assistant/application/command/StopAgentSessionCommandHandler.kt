package com.jorisjonkers.personalstack.assistant.application.command

import com.jorisjonkers.personalstack.assistant.application.rag.LessonAutoCapture
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
    private val autoCapture: LessonAutoCapture,
) : CommandHandler<StopAgentSessionCommand> {
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
        // The stop command is a definitive "this session is done"
        // signal — flush a final auto-capture pass against the
        // full transcript on the way out.
        runCatching { autoCapture.capture(session.id) }
    }
}
