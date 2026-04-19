package com.jorisjonkers.personalstack.auth.infrastructure.web.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class ResendConfirmationRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Must be a valid email address")
    val email: String,
)
