package com.jorisjonkers.personalstack.auth.domain.event

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.common.event.DomainEvent
import java.time.Instant

data class EmailConfirmationRequestedEvent(
    val userId: UserId,
    val username: String,
    val email: String,
    val confirmationToken: String,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
