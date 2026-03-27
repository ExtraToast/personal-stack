package com.jorisjonkers.privatestack.auth.infrastructure.web

import com.jorisjonkers.privatestack.auth.application.command.EnrollTotpCommand
import com.jorisjonkers.privatestack.auth.application.command.EnrollTotpCommandHandler
import com.jorisjonkers.privatestack.auth.application.command.VerifyTotpCommand
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.domain.service.TotpService
import com.jorisjonkers.privatestack.auth.infrastructure.web.dto.TotpEnrollResponse
import com.jorisjonkers.privatestack.auth.infrastructure.web.dto.TotpVerifyRequest
import com.jorisjonkers.privatestack.common.command.CommandBus
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/totp")
class TotpController(
    private val enrollTotpCommandHandler: EnrollTotpCommandHandler,
    private val commandBus: CommandBus,
    private val totpService: TotpService,
) {
    @PostMapping("/enroll")
    fun enroll(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<TotpEnrollResponse> {
        val userId = UserId(UUID.fromString(jwt.subject))
        val secret = enrollTotpCommandHandler.handle(EnrollTotpCommand(userId))
        val qrUri = totpService.generateQrUri(secret, jwt.getClaim("username") ?: jwt.subject)
        return ResponseEntity.ok(TotpEnrollResponse(secret = secret, qrUri = qrUri))
    }

    @PostMapping("/verify")
    fun verify(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: TotpVerifyRequest,
    ): ResponseEntity<Void> {
        val userId = UserId(UUID.fromString(jwt.subject))
        commandBus.dispatch(VerifyTotpCommand(userId = userId, code = request.code))
        return ResponseEntity.noContent().build()
    }
}
