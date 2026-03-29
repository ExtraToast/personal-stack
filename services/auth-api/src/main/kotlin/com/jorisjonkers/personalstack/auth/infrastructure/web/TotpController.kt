package com.jorisjonkers.personalstack.auth.infrastructure.web

import com.jorisjonkers.personalstack.auth.application.command.EnrollTotpCommand
import com.jorisjonkers.personalstack.auth.application.command.EnrollTotpCommandHandler
import com.jorisjonkers.personalstack.auth.application.command.VerifyTotpCommand
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.auth.infrastructure.security.AuthenticatedUser
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.TotpEnrollResponse
import com.jorisjonkers.personalstack.auth.infrastructure.web.dto.TotpVerifyRequest
import com.jorisjonkers.personalstack.common.command.CommandBus
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/totp")
class TotpController(
    private val enrollTotpCommandHandler: EnrollTotpCommandHandler,
    private val commandBus: CommandBus,
    private val totpService: TotpService,
) {
    @PostMapping("/enroll")
    fun enroll(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ResponseEntity<TotpEnrollResponse> {
        val secret = enrollTotpCommandHandler.handle(EnrollTotpCommand(user.userId))
        val qrUri = totpService.generateQrUri(secret, user.username)
        return ResponseEntity.ok(TotpEnrollResponse(secret = secret, qrUri = qrUri))
    }

    @PostMapping("/verify")
    fun verify(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: TotpVerifyRequest,
    ): ResponseEntity<Void> {
        commandBus.dispatch(VerifyTotpCommand(userId = user.userId, code = request.code))
        return ResponseEntity.noContent().build()
    }
}
