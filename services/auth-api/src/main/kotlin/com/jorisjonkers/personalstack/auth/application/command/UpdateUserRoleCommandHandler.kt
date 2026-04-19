package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.common.command.CommandHandlerWithResult
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class UpdateUserRoleCommandHandler(
    private val userRepository: UserRepository,
) : CommandHandlerWithResult<UpdateUserRoleCommand, User> {
    override fun handle(command: UpdateUserRoleCommand): User {
        val user =
            userRepository.findById(command.userId)
                ?: throw NotFoundException("User", command.userId.value.toString())
        val updated = user.copy(role = command.role)
        return userRepository.update(updated)
    }
}
