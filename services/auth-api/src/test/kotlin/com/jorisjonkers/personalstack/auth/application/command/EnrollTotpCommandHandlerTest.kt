package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EnrollTotpCommandHandlerTest {
    private val userRepository = mockk<UserRepository>(relaxUnitFun = true)
    private val totpService = mockk<TotpService>()

    private val handler = EnrollTotpCommandHandler(userRepository, totpService)

    @Test
    fun `handle generates and saves TOTP secret for existing user`() {
        val userId = UserId(UUID.randomUUID())
        val user = buildUser(userId)
        val secret = "JBSWY3DPEHPK3PXP"

        every { userRepository.findById(userId) } returns user
        every { totpService.generateSecret() } returns secret

        val result = handler.handle(EnrollTotpCommand(userId))

        assertThat(result).isEqualTo(secret)
        verify { userRepository.saveTotpSecret(userId, secret) }
    }

    @Test
    fun `handle throws NotFoundException when user does not exist`() {
        val userId = UserId(UUID.randomUUID())

        every { userRepository.findById(userId) } returns null

        assertThatThrownBy {
            handler.handle(EnrollTotpCommand(userId))
        }.isInstanceOf(NotFoundException::class.java)
    }

    private fun buildUser(userId: UserId): User {
        val now = Instant.now()
        return User(
            id = userId,
            username = "alice",
            email = "alice@example.com",
            role = Role.USER,
            totpEnabled = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}
