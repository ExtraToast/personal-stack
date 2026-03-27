package com.jorisjonkers.personalstack.auth.domain.event

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.common.event.DomainEvent
import java.time.Instant

data class UserRegisteredEvent(
    val userId: UserId,
    val username: String,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
