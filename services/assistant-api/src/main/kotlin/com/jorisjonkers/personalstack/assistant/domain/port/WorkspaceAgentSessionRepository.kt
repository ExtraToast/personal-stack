package com.jorisjonkers.personalstack.assistant.domain.port

import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSession
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceId

interface WorkspaceAgentSessionRepository {
    fun save(session: WorkspaceAgentSession): WorkspaceAgentSession

    fun findById(id: WorkspaceAgentSessionId): WorkspaceAgentSession?

    fun findAllByWorkspaceId(workspaceId: WorkspaceId): List<WorkspaceAgentSession>

    fun delete(id: WorkspaceAgentSessionId)
}
