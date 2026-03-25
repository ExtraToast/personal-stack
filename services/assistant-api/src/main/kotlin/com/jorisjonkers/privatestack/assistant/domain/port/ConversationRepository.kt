package com.jorisjonkers.privatestack.assistant.domain.port

import com.jorisjonkers.privatestack.assistant.domain.model.Conversation
import com.jorisjonkers.privatestack.assistant.domain.model.ConversationId
import java.util.UUID

interface ConversationRepository {
    fun findById(id: ConversationId): Conversation?
    fun findByUserId(userId: UUID): List<Conversation>
    fun save(conversation: Conversation): Conversation
}
