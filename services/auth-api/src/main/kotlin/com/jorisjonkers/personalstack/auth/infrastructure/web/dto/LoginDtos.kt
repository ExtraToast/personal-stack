package com.jorisjonkers.personalstack.auth.infrastructure.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    val username: String,
    @field:NotBlank(message = "Password is required")
    val password: String,
)

data class RefreshRequest(
    @field:NotBlank(message = "Refresh token is required")
    val refreshToken: String,
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
)

data class LoginResponse(
    val totpRequired: Boolean,
    val totpChallengeToken: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
    val tokenType: String = "Bearer",
)

data class TotpChallengeRequest(
    @field:NotBlank(message = "Challenge token is required")
    val totpChallengeToken: String,
    @field:NotBlank(message = "TOTP code is required")
    @field:Pattern(regexp = "^\\d{6}$", message = "TOTP code must be 6 digits")
    val code: String,
)
