package com.jorisjonkers.privatestack.assistant.application.query

import com.jorisjonkers.privatestack.assistant.domain.model.Conversation
import com.jorisjonkers.privatestack.assistant.domain.model.ConversationId
import com.jorisjonkers.privatestack.assistant.domain.port.ConversationRepository
import com.jorisjonkers.privatestack.common.exception.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GetConversationQueryService(
    private val conversationRepository: ConversationRepository,
) {
    fun findById(id: ConversationId): Conversation =
        conversationRepository.findById(id)
            ?: throw NotFoundException("Conversation", id.value.toString())

    fun findByUserId(userId: UUID): List<Conversation> = conversationRepository.findByUserId(userId)
}
