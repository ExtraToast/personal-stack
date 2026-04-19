package com.jorisjonkers.personalstack.assistant.infrastructure.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendMessageRequest(
    @field:NotBlank(message = "Content is required")
    @field:Size(max = 10000, message = "Content must not exceed 10000 characters")
    val content: String,
)
