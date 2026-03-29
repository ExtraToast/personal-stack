package com.jorisjonkers.personalstack.auth.infrastructure.web.dto

import jakarta.validation.constraints.NotBlank

data class SessionLoginRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,
    @field:NotBlank(message = "Password is required")
    val password: String,
    val totpCode: String? = null,
)

data class SessionLoginResponse(
    val success: Boolean = false,
    val totpRequired: Boolean = false,
    val user: SessionUserResponse? = null,
)

data class SessionUserResponse(
    val id: String,
    val username: String,
    val role: String,
    val roles: List<String> = emptyList(),
)
