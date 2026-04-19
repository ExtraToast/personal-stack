package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.ConfirmEmailCommand
import com.jorisjonkers.personalstack.auth.application.command.ResendConfirmationCommand
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.ResendConfirmationRequest
import com.jorisjonkers.personalstack.common.command.CommandBus
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class EmailConfirmationController(
    private val commandBus: CommandBus,
) {
    @GetMapping("/confirm-email")
    fun confirmEmail(
        @RequestParam token: String,
    ): ResponseEntity<Map<String, String>> {
        commandBus.dispatch(ConfirmEmailCommand(token))
        return ResponseEntity.ok(mapOf("message" to "Email confirmed successfully"))
    }

    @PostMapping("/resend-confirmation")
    fun resendConfirmation(
        @Valid @RequestBody request: ResendConfirmationRequest,
    ): ResponseEntity<Map<String, String>> {
        commandBus.dispatch(ResendConfirmationCommand(request.email))
        return ResponseEntity.ok(
            mapOf(
                "message" to "If that email exists and is unconfirmed, a confirmation email has been sent",
            ),
        )
    }
}
