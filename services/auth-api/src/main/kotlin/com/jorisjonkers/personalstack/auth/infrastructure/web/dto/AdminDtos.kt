package com.jorisjonkers.personalstack.auth.infrastructure.web.dto

import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.User
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class AdminUserResponse(
    val id: UUID,
    val username: String,
    val email: String,
    val role: String,
    val emailConfirmed: Boolean,
    val totpEnabled: Boolean,
    val servicePermissions: List<String>,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User): AdminUserResponse =
            AdminUserResponse(
                id = user.id.value,
                username = user.username,
                email = user.email,
                role = user.role.name,
                emailConfirmed = user.emailConfirmed,
                totpEnabled = user.totpEnabled,
                servicePermissions = user.servicePermissions.map { it.name }.sorted(),
                createdAt = user.createdAt,
            )
    }
}

data class UpdateRoleRequest(
    @field:NotBlank
    val role: String,
)

data class UpdateServicePermissionsRequest(
    val services: List<String>,
)

fun UpdateServicePermissionsRequest.toServicePermissions(): Set<ServicePermission> =
    services.mapNotNull { name ->
        runCatching { ServicePermission.valueOf(name.uppercase()) }.getOrNull()
    }.toSet()
