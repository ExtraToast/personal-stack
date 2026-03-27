package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.event.EmailConfirmationRequestedEvent
import com.jorisjonkers.personalstack.auth.domain.model.EmailConfirmationToken
import com.jorisjonkers.personalstack.auth.domain.port.EmailConfirmationTokenRepository
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ResendConfirmationCommandHandler(
    private val userRepository: UserRepository,
    private val emailConfirmationTokenRepository: EmailConfirmationTokenRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : CommandHandler<ResendConfirmationCommand> {
    override fun handle(command: ResendConfirmationCommand) {
        val user = userRepository.findByEmail(command.email) ?: return

        if (user.emailConfirmed) return

        emailConfirmationTokenRepository.deleteByUserId(user.id)

        val now = Instant.now()
        val confirmationToken =
            EmailConfirmationToken(
                id = UUID.randomUUID(),
                userId = user.id,
                token = UUID.randomUUID().toString(),
                expiresAt = now.plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS),
                usedAt = null,
                createdAt = now,
            )
        emailConfirmationTokenRepository.save(confirmationToken)

        eventPublisher.publishEvent(
            EmailConfirmationRequestedEvent(
                userId = user.id,
                username = user.username,
                email = user.email,
                confirmationToken = confirmationToken.token,
            ),
        )
    }

    companion object {
        private const val TOKEN_EXPIRY_HOURS = 24L
    }
}
