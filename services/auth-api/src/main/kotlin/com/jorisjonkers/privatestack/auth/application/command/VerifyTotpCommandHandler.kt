package com.jorisjonkers.privatestack.auth.application.command

import com.jorisjonkers.privatestack.auth.domain.service.TotpService
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import com.jorisjonkers.privatestack.common.command.CommandHandler
import com.jorisjonkers.privatestack.common.exception.DomainException
import com.jorisjonkers.privatestack.common.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class VerifyTotpCommandHandler(
    private val userRepository: UserRepository,
    private val totpService: TotpService,
) : CommandHandler<VerifyTotpCommand> {

    override fun handle(command: VerifyTotpCommand) {
        val user = userRepository.findById(command.userId)
            ?: throw NotFoundException("User", command.userId.value.toString())

        val credentials = userRepository.findCredentialsByUsername(user.username)
            ?: throw NotFoundException("User credentials", command.userId.value.toString())

        val secret = credentials.totpSecret
            ?: throw InvalidTotpStateException("TOTP enrollment not started — call enroll first")

        if (!totpService.verifyCode(secret, command.code)) {
            throw InvalidTotpCodeException()
        }

        userRepository.update(user.copy(totpEnabled = true))
    }
}

class InvalidTotpStateException(message: String) : DomainException(message, "INVALID_TOTP_STATE")
class InvalidTotpCodeException : DomainException("Invalid or expired TOTP code", "INVALID_TOTP_CODE")
