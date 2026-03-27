package com.jorisjonkers.personalstack.assistant.domain.port

import com.jorisjonkers.personalstack.assistant.domain.model.Conversation
import com.jorisjonkers.personalstack.assistant.domain.model.ConversationId
import java.util.UUID

interface ConversationRepository {
    fun findById(id: ConversationId): Conversation?

    fun findByUserId(userId: UUID): List<Conversation>

    fun save(conversation: Conversation): Conversation
}
