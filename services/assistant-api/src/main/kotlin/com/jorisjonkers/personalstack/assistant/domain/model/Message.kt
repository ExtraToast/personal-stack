package com.jorisjonkers.personalstack.assistant.domain.model

import java.time.Instant

data class Message(
    val id: MessageId,
    val conversationId: ConversationId,
    val role: MessageRole,
    val content: String,
    val createdAt: Instant,
)
