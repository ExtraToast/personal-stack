package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.common.command.CommandHandler
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class DeleteUserCommandHandler(
    private val userRepository: UserRepository,
) : CommandHandler<DeleteUserCommand> {
    override fun handle(command: DeleteUserCommand) {
        userRepository.findById(command.userId)
            ?: throw NotFoundException("User", command.userId.value.toString())
        userRepository.deleteById(command.userId)
    }
}
