package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.common.command.CommandHandlerWithResult
import com.jorisjonkers.personalstack.common.exception.DomainException
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class EnrollTotpCommandHandler(
    private val userRepository: UserRepository,
    private val totpService: TotpService,
) : CommandHandlerWithResult<EnrollTotpCommand, String> {
    override fun handle(command: EnrollTotpCommand): String {
        val user =
            userRepository.findById(command.userId)
                ?: throw NotFoundException("User", command.userId.value.toString())

        if (user.totpEnabled) {
            throw TotpAlreadyEnrolledException()
        }

        val secret = totpService.generateSecret()
        userRepository.saveTotpSecret(command.userId, secret)
        return secret
    }
}

class TotpAlreadyEnrolledException :
    DomainException("TOTP is already enabled for this account", "TOTP_ALREADY_ENROLLED")
