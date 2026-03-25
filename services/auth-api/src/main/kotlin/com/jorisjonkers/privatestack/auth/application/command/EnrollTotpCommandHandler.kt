package com.jorisjonkers.privatestack.auth.application.command

import com.jorisjonkers.privatestack.auth.domain.service.TotpService
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import com.jorisjonkers.privatestack.common.command.CommandHandlerWithResult
import com.jorisjonkers.privatestack.common.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class EnrollTotpCommandHandler(
    private val userRepository: UserRepository,
    private val totpService: TotpService,
) : CommandHandlerWithResult<EnrollTotpCommand, String> {

    override fun handle(command: EnrollTotpCommand): String {
        userRepository.findById(command.userId)
            ?: throw NotFoundException("User", command.userId.value.toString())

        val secret = totpService.generateSecret()
        userRepository.saveTotpSecret(command.userId, secret)
        return secret
    }
}
