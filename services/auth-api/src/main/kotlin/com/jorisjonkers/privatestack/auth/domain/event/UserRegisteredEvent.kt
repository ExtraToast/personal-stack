package com.jorisjonkers.privatestack.auth.domain.event

import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.common.event.DomainEvent
import java.time.Instant

data class UserRegisteredEvent(
    val userId: UserId,
    val username: String,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
