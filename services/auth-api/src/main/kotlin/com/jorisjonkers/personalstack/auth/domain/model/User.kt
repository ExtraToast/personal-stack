package com.jorisjonkers.personalstack.auth.domain.model

import java.time.Instant

data class User(
    val id: UserId,
    val username: String,
    val email: String,
    val role: Role,
    val totpEnabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
