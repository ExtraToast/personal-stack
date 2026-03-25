package com.jorisjonkers.privatestack.assistant.domain.event

import com.jorisjonkers.privatestack.assistant.domain.model.ConversationId
import com.jorisjonkers.privatestack.common.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class ConversationStartedEvent(
    val conversationId: ConversationId,
    val userId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
