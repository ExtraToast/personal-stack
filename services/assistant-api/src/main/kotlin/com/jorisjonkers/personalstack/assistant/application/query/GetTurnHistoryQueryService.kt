package com.jorisjonkers.personalstack.assistant.application.query

import com.jorisjonkers.personalstack.assistant.domain.model.Turn
import com.jorisjonkers.personalstack.assistant.domain.model.WorkspaceAgentSessionId
import com.jorisjonkers.personalstack.assistant.domain.port.TurnRepository
import org.springframework.stereotype.Service

@Service
class GetTurnHistoryQueryService(private val turns: TurnRepository) {
    fun history(sessionId: WorkspaceAgentSessionId, limit: Int = 200): List<Turn> =
        turns.findBySessionId(sessionId, limit)
}
