package com.jorisjonkers.personalstack.assistant.domain.port

import com.jorisjonkers.personalstack.assistant.domain.model.ConversationId
import com.jorisjonkers.personalstack.assistant.domain.model.Message

interface MessageRepository {
    fun save(message: Message): Message

    fun findByConversationId(id: ConversationId): List<Message>
}
