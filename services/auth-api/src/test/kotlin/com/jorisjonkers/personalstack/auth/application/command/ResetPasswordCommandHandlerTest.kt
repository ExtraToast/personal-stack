package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.exception.InvalidResetTokenException
import com.jorisjonkers.personalstack.auth.domain.model.PasswordResetToken
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.PasswordResetTokenRepository
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ResetPasswordCommandHandlerTest {
    private val passwordResetTokenRepository = mockk<PasswordResetTokenRepository>()
    private val userRepository = mockk<UserRepository>(relaxUnitFun = true)
    private val passwordEncoder = mockk<PasswordEncoder>()

    private val handler =
        ResetPasswordCommandHandler(
            passwordResetTokenRepository,
            userRepository,
            passwordEncoder,
        )

    @Test
    fun `handle resets password with valid token`() {
        val userId = UserId(UUID.randomUUID())
        val now = Instant.now()
        val token =
            PasswordResetToken(
                id = UUID.randomUUID(),
                userId = userId,
                token = "valid-token",
                expiresAt = now.plus(1, ChronoUnit.HOURS),
                usedAt = null,
                createdAt = now,
            )
        val command = ResetPasswordCommand(token = "valid-token", newPassword = "newpass123")
        val savedTokenSlot = slot<PasswordResetToken>()

        every { passwordResetTokenRepository.findByToken("valid-token") } returns token
        every { passwordEncoder.encode("newpass123") } returns "\$2a\$10\$newhash"
        every { passwordResetTokenRepository.save(capture(savedTokenSlot)) } answers { savedTokenSlot.captured }

        handler.handle(command)

        verify { userRepository.updatePassword(userId, "\$2a\$10\$newhash") }
        assertThat(savedTokenSlot.captured.usedAt).isNotNull()
    }

    @Test
    fun `handle throws InvalidResetTokenException when token not found`() {
        val command = ResetPasswordCommand(token = "unknown-token", newPassword = "newpass123")

        every { passwordResetTokenRepository.findByToken("unknown-token") } returns null

        assertThatThrownBy {
            handler.handle(command)
        }.isInstanceOf(InvalidResetTokenException::class.java)
    }

    @Test
    fun `handle throws InvalidResetTokenException when token is expired`() {
        val userId = UserId(UUID.randomUUID())
        val now = Instant.now()
        val token =
            PasswordResetToken(
                id = UUID.randomUUID(),
                userId = userId,
                token = "expired-token",
                expiresAt = now.minus(1, ChronoUnit.HOURS),
                usedAt = null,
                createdAt = now.minus(2, ChronoUnit.HOURS),
            )
        val command = ResetPasswordCommand(token = "expired-token", newPassword = "newpass123")

        every { passwordResetTokenRepository.findByToken("expired-token") } returns token

        assertThatThrownBy {
            handler.handle(command)
        }.isInstanceOf(InvalidResetTokenException::class.java)
    }

    @Test
    fun `handle throws InvalidResetTokenException when token is already used`() {
        val userId = UserId(UUID.randomUUID())
        val now = Instant.now()
        val token =
            PasswordResetToken(
                id = UUID.randomUUID(),
                userId = userId,
                token = "used-token",
                expiresAt = now.plus(1, ChronoUnit.HOURS),
                usedAt = now.minus(30, ChronoUnit.MINUTES),
                createdAt = now.minus(1, ChronoUnit.HOURS),
            )
        val command = ResetPasswordCommand(token = "used-token", newPassword = "newpass123")

        every { passwordResetTokenRepository.findByToken("used-token") } returns token

        assertThatThrownBy {
            handler.handle(command)
        }.isInstanceOf(InvalidResetTokenException::class.java)
    }
}
