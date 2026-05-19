package com.jorisjonkers.personalstack.assistant.domain.port

import com.jorisjonkers.personalstack.assistant.domain.model.ChatSession
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSessionId
import java.util.UUID

interface ChatSessionRepository {
    fun save(session: ChatSession): ChatSession

    fun findById(id: ChatSessionId): ChatSession?

    fun findAllByUserId(userId: UUID): List<ChatSession>

    fun delete(id: ChatSessionId)
}
