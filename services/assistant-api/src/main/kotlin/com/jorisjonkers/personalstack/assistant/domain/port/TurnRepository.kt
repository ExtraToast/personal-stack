package com.jorisjonkers.personalstack.assistant.domain.port

import com.jorisjonkers.personalstack.assistant.domain.model.Turn
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId

interface TurnRepository {
    fun save(turn: Turn): Turn

    fun findBySessionId(
        sessionId: WorkspaceAgentSessionId,
        limit: Int = 200,
    ): List<Turn>
}
