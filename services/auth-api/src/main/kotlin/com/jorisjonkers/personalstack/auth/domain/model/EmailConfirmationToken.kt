package com.jorisjonkers.personalstack.auth.domain.model

import java.time.Instant
import java.util.UUID

data class EmailConfirmationToken(
    val id: UUID,
    val userId: UserId,
    val token: String,
    val expiresAt: Instant,
    val usedAt: Instant?,
    val createdAt: Instant,
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun isUsed(): Boolean = usedAt != null
}
