package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.ChangePasswordCommand
import com.jorisjonkers.personalstack.auth.application.command.ForgotPasswordCommand
import com.jorisjonkers.personalstack.auth.application.command.ResetPasswordCommand
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.ChangePasswordRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.ForgotPasswordRequest
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.ResetPasswordRequest
import com.jorisjonkers.personalstack.common.command.CommandBus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class PasswordController(
    private val commandBus: CommandBus,
) {
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: ChangePasswordRequest,
    ) {
        commandBus.dispatch(
            ChangePasswordCommand(
                userId = user.userIdValue(),
                currentPassword = request.currentPassword,
                newPassword = request.newPassword,
            ),
        )
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest,
    ): ResponseEntity<Map<String, String>> {
        commandBus.dispatch(ForgotPasswordCommand(email = request.email))
        return ResponseEntity.ok(
            mapOf("message" to "If an account with that email exists, a password reset link has been sent."),
        )
    }

    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @RequestBody request: ResetPasswordRequest,
    ): ResponseEntity<Map<String, String>> {
        commandBus.dispatch(
            ResetPasswordCommand(
                token = request.token,
                newPassword = request.newPassword,
            ),
        )
        return ResponseEntity.ok(mapOf("message" to "Password has been reset successfully."))
    }
}
