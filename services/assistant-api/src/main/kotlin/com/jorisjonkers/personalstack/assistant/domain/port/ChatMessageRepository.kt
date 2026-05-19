package com.jorisjonkers.personalstack.assistant.domain.port

import com.jorisjonkers.personalstack.assistant.domain.model.ChatMessage
import com.jorisjonkers.personalstack.assistant.domain.model.ChatMessageId
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSessionId

interface ChatMessageRepository {
    fun save(message: ChatMessage): ChatMessage

    fun findById(id: ChatMessageId): ChatMessage?

    fun findAllBySessionIdOrderedByTime(sessionId: ChatSessionId): List<ChatMessage>

    fun deleteAllBySessionId(sessionId: ChatSessionId)
}
