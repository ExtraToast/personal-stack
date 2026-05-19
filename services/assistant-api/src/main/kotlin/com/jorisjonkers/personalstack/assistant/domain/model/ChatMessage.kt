package com.jorisjonkers.personalstack.assistant.domain.model

import java.time.Instant

data class ChatMessage(
    val id: ChatMessageId,
    val sessionId: ChatSessionId,
    val role: ChatMessageRole,
    val body: String,
    val createdAt: Instant,
)
