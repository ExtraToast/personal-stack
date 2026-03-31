package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.exception.UserNotFoundException
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class UpdateProfileCommandHandler(
    private val userRepository: UserRepository,
) : CommandHandler<UpdateProfileCommand> {
    override fun handle(command: UpdateProfileCommand) {
        val user =
            userRepository.findById(command.userId)
                ?: throw UserNotFoundException(command.userId)
        userRepository.update(
            user.copy(
                firstName = command.firstName,
                lastName = command.lastName,
                updatedAt = Instant.now(),
            ),
        )
    }
}
