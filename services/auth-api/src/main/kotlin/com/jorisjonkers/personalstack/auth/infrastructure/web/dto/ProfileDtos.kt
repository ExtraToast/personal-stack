package com.jorisjonkers.personalstack.auth.infrastructure.web.dto

import com.jorisjonkers.personalstack.auth.domain.model.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class UpdateProfileRequest(
    @field:NotBlank(message = "First name is required")
    @field:Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    val firstName: String,
    @field:NotBlank(message = "Last name is required")
    @field:Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    val lastName: String,
)

data class ProfileResponse(
    val id: UUID,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val totpEnabled: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User): ProfileResponse =
            ProfileResponse(
                id = user.id.value,
                username = user.username,
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                role = user.role.name,
                totpEnabled = user.totpEnabled,
                createdAt = user.createdAt,
            )
    }
}
