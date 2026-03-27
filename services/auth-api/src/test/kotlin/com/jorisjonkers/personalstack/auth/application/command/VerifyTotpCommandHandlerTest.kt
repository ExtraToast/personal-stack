package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class VerifyTotpCommandHandlerTest {
    private val userRepository = mockk<UserRepository>(relaxUnitFun = true)
    private val totpService = mockk<TotpService>()

    private val handler = VerifyTotpCommandHandler(userRepository, totpService)

    @Test
    fun `handle verifies code and enables TOTP on user`() {
        val userId = UserId(UUID.randomUUID())
        val user = buildUser(userId)
        val secret = "JBSWY3DPEHPK3PXP"
        val credentials = buildCredentials(userId, user.username, totpSecret = secret)

        every { userRepository.findById(userId) } returns user
        every { userRepository.findCredentialsByUsername(user.username) } returns credentials
        every { totpService.verifyCode(secret, "123456") } returns true
        every { userRepository.update(any()) } answers { firstArg() }

        handler.handle(VerifyTotpCommand(userId, "123456"))

        verify { userRepository.update(user.copy(totpEnabled = true)) }
    }

    @Test
    fun `handle throws InvalidTotpCodeException for wrong code`() {
        val userId = UserId(UUID.randomUUID())
        val user = buildUser(userId)
        val secret = "JBSWY3DPEHPK3PXP"
        val credentials = buildCredentials(userId, user.username, totpSecret = secret)

        every { userRepository.findById(userId) } returns user
        every { userRepository.findCredentialsByUsername(user.username) } returns credentials
        every { totpService.verifyCode(secret, "000000") } returns false

        assertThatThrownBy {
            handler.handle(VerifyTotpCommand(userId, "000000"))
        }.isInstanceOf(InvalidTotpCodeException::class.java)
    }

    @Test
    fun `handle throws InvalidTotpStateException when no TOTP secret exists`() {
        val userId = UserId(UUID.randomUUID())
        val user = buildUser(userId)
        val credentials = buildCredentials(userId, user.username, totpSecret = null)

        every { userRepository.findById(userId) } returns user
        every { userRepository.findCredentialsByUsername(user.username) } returns credentials

        assertThatThrownBy {
            handler.handle(VerifyTotpCommand(userId, "123456"))
        }.isInstanceOf(InvalidTotpStateException::class.java)
    }

    @Test
    fun `handle throws NotFoundException when user does not exist`() {
        val userId = UserId(UUID.randomUUID())

        every { userRepository.findById(userId) } returns null

        assertThatThrownBy {
            handler.handle(VerifyTotpCommand(userId, "123456"))
        }.isInstanceOf(NotFoundException::class.java)
    }

    private fun buildUser(userId: UserId): User {
        val now = Instant.now()
        return User(
            id = userId,
            username = "alice",
            email = "alice@example.com",
            role = Role.USER,
            emailConfirmed = true,
            totpEnabled = false,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun buildCredentials(
        userId: UserId,
        username: String,
        totpSecret: String?,
    ): UserCredentials =
        UserCredentials(
            userId = userId,
            username = username,
            passwordHash = "\$2a\$10\$hashed",
            totpSecret = totpSecret,
            totpEnabled = false,
            emailConfirmed = true,
            role = Role.USER,
        )
}
