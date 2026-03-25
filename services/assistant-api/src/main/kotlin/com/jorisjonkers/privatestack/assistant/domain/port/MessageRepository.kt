package com.jorisjonkers.privatestack.assistant.domain.port

import com.jorisjonkers.privatestack.assistant.domain.model.ConversationId
import com.jorisjonkers.privatestack.assistant.domain.model.Message

interface MessageRepository {
    fun save(message: Message): Message
    fun findByConversationId(id: ConversationId): List<Message>
}
