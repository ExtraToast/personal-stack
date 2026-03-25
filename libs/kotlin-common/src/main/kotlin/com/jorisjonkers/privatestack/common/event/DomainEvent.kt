package com.jorisjonkers.privatestack.common.event

import java.time.Instant

interface DomainEvent {
    val occurredAt: Instant
}
