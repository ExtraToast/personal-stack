package com.jorisjonkers.personalstack.assistant.infrastructure.web.dto

import com.jorisjonkers.personalstack.assistant.domain.model.ChatMessage
import com.jorisjonkers.personalstack.assistant.domain.model.ChatMessageRole
import com.jorisjonkers.personalstack.assistant.domain.model.ChatSession
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class StartChatSessionRequest(
    @field:Size(max = 120) val title: String? = null,
)

data class ChatSessionResponse(
    val id: UUID,
    val userId: UUID,
    val title: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(s: ChatSession) =
            ChatSessionResponse(
                id = s.id.value,
                userId = s.userId,
                title = s.title,
                status = s.status.name,
                createdAt = s.createdAt,
                updatedAt = s.updatedAt,
            )
    }
}

data class AppendChatMessageRequest(
    @field:NotBlank val body: String,
    val role: ChatMessageRole = ChatMessageRole.USER,
)

data class ChatMessageResponse(
    val id: UUID,
    val sessionId: UUID,
    val role: String,
    val body: String,
    val createdAt: Instant,
) {
    companion object {
        fun of(m: ChatMessage) =
            ChatMessageResponse(
                id = m.id.value,
                sessionId = m.sessionId.value,
                role = m.role.name,
                body = m.body,
                createdAt = m.createdAt,
            )
    }
}
