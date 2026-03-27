package com.jorisjonkers.personalstack.assistant.domain.event

import com.jorisjonkers.personalstack.assistant.domain.model.ConversationId
import com.jorisjonkers.personalstack.common.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class ConversationStartedEvent(
    val conversationId: ConversationId,
    val userId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
