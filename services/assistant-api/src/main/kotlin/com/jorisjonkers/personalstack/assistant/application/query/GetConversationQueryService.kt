package com.jorisjonkers.personalstack.assistant.application.query

import com.jorisjonkers.personalstack.assistant.domain.model.Conversation
import com.jorisjonkers.personalstack.assistant.domain.model.ConversationId
import com.jorisjonkers.personalstack.assistant.domain.port.ConversationRepository
import com.jorisjonkers.personalstack.common.exception.NotFoundException
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
