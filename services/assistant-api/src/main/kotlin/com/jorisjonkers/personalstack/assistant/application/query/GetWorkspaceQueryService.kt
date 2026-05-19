package com.jorisjonkers.personalstack.assistant.application.query

import com.jorisjonkers.personalstack.assistant.domain.model.Workspace
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSession
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceAgentSessionRepository
import com.jorisjonkers.personalstack.assistant.domain.port.WorkspaceRepository
import org.springframework.stereotype.Service

@Service
class GetWorkspaceQueryService(
    private val workspaces: WorkspaceRepository,
    private val sessions: WorkspaceAgentSessionRepository,
) {
    data class WorkspaceView(
        val workspace: Workspace,
        val sessions: List<WorkspaceAgentSession>,
    )

    fun get(id: WorkspaceId): WorkspaceView? {
        val workspace = workspaces.findById(id) ?: return null
        return WorkspaceView(workspace, sessions.findAllByWorkspaceId(id))
    }
}
