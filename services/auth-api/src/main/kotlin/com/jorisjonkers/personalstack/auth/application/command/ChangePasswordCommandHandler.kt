package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.exception.InvalidPasswordException
import com.jorisjonkers.personalstack.auth.domain.exception.UserNotFoundException
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.stereotype.Service

@Service
class ChangePasswordCommandHandler(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : CommandHandler<ChangePasswordCommand> {
    @Suppress("ThrowsCount")
    override fun handle(command: ChangePasswordCommand) {
        val user =
            userRepository.findById(command.userId)
                ?: throw UserNotFoundException(command.userId)
        val credentials =
            userRepository.findCredentialsByUsername(user.username)
                ?: throw UserNotFoundException(command.userId)

        if (!passwordEncoder.matches(command.currentPassword, credentials.passwordHash)) {
            throw InvalidPasswordException()
        }

        val newHash = passwordEncoder.encode(command.newPassword)
        userRepository.updatePassword(command.userId, newHash)
    }
}
