package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.common.command.CommandHandler
import com.jorisjonkers.personalstack.common.exception.DomainException
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import org.springframework.stereotype.Service

@Service
class VerifyTotpCommandHandler(
    private val userRepository: UserRepository,
    private val totpService: TotpService,
) : CommandHandler<VerifyTotpCommand> {
    override fun handle(command: VerifyTotpCommand) {
        val user =
            userRepository.findById(command.userId)
                ?: throw NotFoundException("User", command.userId.value.toString())

        val secret = findTotpSecret(user)

        if (!totpService.verifyCode(secret, command.code)) {
            throw InvalidTotpCodeException()
        }

        userRepository.update(user.copy(totpEnabled = true))
    }

    private fun findTotpSecret(user: User): String {
        val credentials =
            userRepository.findCredentialsByUsername(user.username)
                ?: throw NotFoundException("User credentials", user.id.value.toString())

        return credentials.totpSecret
            ?: throw InvalidTotpStateException("TOTP enrollment not started — call enroll first")
    }
}

class InvalidTotpStateException(
    message: String,
) : DomainException(message, "INVALID_TOTP_STATE")

class InvalidTotpCodeException : DomainException("Invalid or expired TOTP code", "INVALID_TOTP_CODE")
