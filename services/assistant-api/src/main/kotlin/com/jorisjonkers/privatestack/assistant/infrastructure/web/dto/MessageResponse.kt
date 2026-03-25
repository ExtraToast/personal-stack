package com.jorisjonkers.privatestack.assistant.infrastructure.web.dto

import com.jorisjonkers.privatestack.assistant.domain.model.Message
import java.time.Instant
import java.util.UUID

data class MessageResponse(
    val id: UUID,
    val conversationId: UUID,
    val role: String,
    val content: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(message: Message): MessageResponse =
            MessageResponse(
                id = message.id.value,
                conversationId = message.conversationId.value,
                role = message.role.name,
                content = message.content,
                createdAt = message.createdAt,
            )
    }
}
