package com.jorisjonkers.privatestack.assistant.application.query

import com.jorisjonkers.privatestack.assistant.domain.model.ConversationId
import com.jorisjonkers.privatestack.assistant.domain.model.Message
import com.jorisjonkers.privatestack.assistant.domain.port.MessageRepository
import org.springframework.stereotype.Service

@Service
class GetMessageQueryService(
    private val messageRepository: MessageRepository,
) {
    fun findByConversationId(conversationId: ConversationId): List<Message> =
        messageRepository.findByConversationId(conversationId)
}
