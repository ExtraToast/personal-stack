package com.jorisjonkers.privatestack.auth.application.command

import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import com.jorisjonkers.privatestack.common.command.CommandHandler
import org.springframework.stereotype.Service

@Service
class RegisterUserCommandHandler(
    private val userRepository: UserRepository,
) : CommandHandler<RegisterUserCommand> {

    override fun handle(command: RegisterUserCommand) {
        require(!userRepository.existsByUsername(command.username)) {
            "Username already taken: ${command.username}"
        }
        require(!userRepository.existsByEmail(command.email)) {
            "Email already taken: ${command.email}"
        }
        // TODO: implement user creation logic
    }
}
