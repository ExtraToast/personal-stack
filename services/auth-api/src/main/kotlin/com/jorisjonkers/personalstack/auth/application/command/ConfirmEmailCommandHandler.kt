package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.exception.InvalidConfirmationTokenException
import com.jorisjonkers.personalstack.auth.domain.port.EmailConfirmationTokenRepository
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ConfirmEmailCommandHandler(
    private val emailConfirmationTokenRepository: EmailConfirmationTokenRepository,
    private val userRepository: UserRepository,
) : CommandHandler<ConfirmEmailCommand> {
    @Suppress("ThrowsCount")
    override fun handle(command: ConfirmEmailCommand) {
        val token =
            emailConfirmationTokenRepository.findByToken(command.token)
                ?: throw InvalidConfirmationTokenException("Confirmation token not found")

        if (token.isUsed()) {
            throw InvalidConfirmationTokenException("Confirmation token has already been used")
        }

        if (token.isExpired()) {
            throw InvalidConfirmationTokenException("Confirmation token has expired")
        }

        val user =
            userRepository.findById(token.userId)
                ?: throw InvalidConfirmationTokenException("User not found for confirmation token")

        userRepository.update(user.copy(emailConfirmed = true, updatedAt = Instant.now()))

        emailConfirmationTokenRepository.save(token.copy(usedAt = Instant.now()))
    }
}
