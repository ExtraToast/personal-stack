package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.exception.InvalidPasswordException
import com.jorisjonkers.personalstack.auth.domain.exception.UserNotFoundException
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserCredentials
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ChangePasswordCommandHandlerTest {
    private val userRepository = mockk<UserRepository>(relaxUnitFun = true)
    private val passwordEncoder = mockk<PasswordEncoder>()

    private val handler = ChangePasswordCommandHandler(userRepository, passwordEncoder)

    @Test
    fun `handle changes password when current password matches`() {
        val userId = UserId(UUID.randomUUID())
        val now = Instant.now()
        val user =
            User(
                id = userId,
                username = "alice",
                email = "alice@example.com",
                firstName = "Alice",
                lastName = "Smith",
                role = Role.USER,
                emailConfirmed = true,
                totpEnabled = false,
                createdAt = now,
                updatedAt = now,
            )
        val credentials =
            UserCredentials(
                userId = userId,
                username = "alice",
                email = "alice@example.com",
                firstName = "Alice",
                lastName = "Smith",
                passwordHash = "\$2a\$10\$oldhash",
                totpSecret = null,
                totpEnabled = false,
                emailConfirmed = true,
                role = Role.USER,
            )
        val command =
            ChangePasswordCommand(
                userId = userId,
                currentPassword = "oldpass",
                newPassword = "newpass",
            )

        every { userRepository.findById(userId) } returns user
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("oldpass", "\$2a\$10\$oldhash") } returns true
        every { passwordEncoder.encode("newpass") } returns "\$2a\$10\$newhash"

        handler.handle(command)

        verify { userRepository.updatePassword(userId, "\$2a\$10\$newhash") }
    }

    @Test
    fun `handle throws InvalidPasswordException when current password is wrong`() {
        val userId = UserId(UUID.randomUUID())
        val now = Instant.now()
        val user =
            User(
                id = userId,
                username = "alice",
                email = "alice@example.com",
                firstName = "Alice",
                lastName = "Smith",
                role = Role.USER,
                emailConfirmed = true,
                totpEnabled = false,
                createdAt = now,
                updatedAt = now,
            )
        val credentials =
            UserCredentials(
                userId = userId,
                username = "alice",
                email = "alice@example.com",
                firstName = "Alice",
                lastName = "Smith",
                passwordHash = "\$2a\$10\$oldhash",
                totpSecret = null,
                totpEnabled = false,
                emailConfirmed = true,
                role = Role.USER,
            )
        val command =
            ChangePasswordCommand(
                userId = userId,
                currentPassword = "wrongpass",
                newPassword = "newpass",
            )

        every { userRepository.findById(userId) } returns user
        every { userRepository.findCredentialsByUsername("alice") } returns credentials
        every { passwordEncoder.matches("wrongpass", "\$2a\$10\$oldhash") } returns false

        assertThatThrownBy {
            handler.handle(command)
        }.isInstanceOf(InvalidPasswordException::class.java)
    }

    @Test
    fun `handle throws UserNotFoundException when user not found`() {
        val userId = UserId(UUID.randomUUID())
        val command =
            ChangePasswordCommand(
                userId = userId,
                currentPassword = "oldpass",
                newPassword = "newpass",
            )

        every { userRepository.findById(userId) } returns null

        assertThatThrownBy {
            handler.handle(command)
        }.isInstanceOf(UserNotFoundException::class.java)
    }
}
