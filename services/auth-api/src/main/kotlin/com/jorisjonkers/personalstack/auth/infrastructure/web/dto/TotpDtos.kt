package com.jorisjonkers.personalstack.auth.infrastructure.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class TotpEnrollResponse(
    val secret: String,
    val qrUri: String,
)

data class TotpVerifyRequest(
    @field:NotBlank(message = "TOTP code is required")
    @field:Pattern(regexp = "^\\d{6}$", message = "TOTP code must be 6 digits")
    val code: String,
)
