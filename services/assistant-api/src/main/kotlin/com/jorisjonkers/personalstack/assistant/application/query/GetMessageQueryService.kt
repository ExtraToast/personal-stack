package com.jorisjonkers.personalstack.assistant.application.query

import com.jorisjonkers.personalstack.assistant.domain.model.ConversationId
import com.jorisjonkers.personalstack.assistant.domain.model.Message
import com.jorisjonkers.personalstack.assistant.domain.port.MessageRepository
import org.springframework.stereotype.Service

@Service
class GetMessageQueryService(
    private val messageRepository: MessageRepository,
) {
    fun findByConversationId(conversationId: ConversationId): List<Message> =
        messageRepository.findByConversationId(conversationId)
}
