package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.event.PasswordResetRequestedEvent
import com.jorisjonkers.personalstack.auth.domain.model.PasswordResetToken
import com.jorisjonkers.personalstack.auth.domain.port.PasswordResetTokenRepository
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ForgotPasswordCommandHandler(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : CommandHandler<ForgotPasswordCommand> {
    override fun handle(command: ForgotPasswordCommand) {
        val user = userRepository.findByEmail(command.email) ?: return

        val token =
            PasswordResetToken(
                id = UUID.randomUUID(),
                userId = user.id,
                token = UUID.randomUUID().toString(),
                expiresAt = Instant.now().plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS),
                usedAt = null,
                createdAt = Instant.now(),
            )
        passwordResetTokenRepository.save(token)

        eventPublisher.publishEvent(
            PasswordResetRequestedEvent(
                userId = user.id,
                username = user.username,
                email = user.email,
                resetToken = token.token,
            ),
        )
    }

    companion object {
        private const val TOKEN_EXPIRY_HOURS = 1L
    }
}
