package com.jorisjonkers.privatestack.assistant.infrastructure.persistence

import com.jorisjonkers.privatestack.assistant.domain.model.Conversation
import com.jorisjonkers.privatestack.assistant.domain.model.ConversationId
import com.jorisjonkers.privatestack.assistant.domain.port.ConversationRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class JooqConversationRepository : ConversationRepository {

    override fun findById(id: ConversationId): Conversation? {
        TODO("Implement with jOOQ after code generation")
    }

    override fun findByUserId(userId: UUID): List<Conversation> {
        TODO("Implement with jOOQ after code generation")
    }

    override fun save(conversation: Conversation): Conversation {
        TODO("Implement with jOOQ after code generation")
    }
}
