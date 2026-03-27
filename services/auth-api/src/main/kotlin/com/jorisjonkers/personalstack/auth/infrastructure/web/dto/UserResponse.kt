package com.jorisjonkers.personalstack.auth.infrastructure.web.dto

import com.jorisjonkers.personalstack.auth.domain.model.User
import java.time.Instant
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val username: String,
    val email: String,
    val role: String,
    val totpEnabled: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(
                id = user.id.value,
                username = user.username,
                email = user.email,
                role = user.role.name,
                totpEnabled = user.totpEnabled,
                createdAt = user.createdAt,
            )
    }
}
