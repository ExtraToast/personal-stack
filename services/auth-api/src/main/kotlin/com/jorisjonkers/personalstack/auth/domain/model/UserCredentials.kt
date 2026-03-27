package com.jorisjonkers.personalstack.auth.domain.model

data class UserCredentials(
    val userId: UserId,
    val username: String,
    val passwordHash: String,
    val totpSecret: String?,
    val totpEnabled: Boolean,
    val role: Role,
)
