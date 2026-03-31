package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.exception.UserNotFoundException
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UpdateProfileCommandHandlerTest {
    private val userRepository = mockk<UserRepository>()

    private val handler = UpdateProfileCommandHandler(userRepository)

    @Test
    fun `handle updates user firstName and lastName`() {
        val userId = UserId(UUID.randomUUID())
        val now = Instant.now()
        val existingUser =
            User(
                id = userId,
                username = "alice",
                email = "alice@example.com",
                firstName = "Alice",
                lastName = "Old",
                role = Role.USER,
                emailConfirmed = true,
                totpEnabled = false,
                createdAt = now,
                updatedAt = now,
            )
        val command =
            UpdateProfileCommand(
                userId = userId,
                firstName = "Alice",
                lastName = "New",
            )
        val userSlot = slot<User>()

        every { userRepository.findById(userId) } returns existingUser
        every { userRepository.update(capture(userSlot)) } answers { userSlot.captured }

        handler.handle(command)

        assertThat(userSlot.captured.firstName).isEqualTo("Alice")
        assertThat(userSlot.captured.lastName).isEqualTo("New")
        verify { userRepository.update(any()) }
    }

    @Test
    fun `handle throws UserNotFoundException when user not found`() {
        val userId = UserId(UUID.randomUUID())
        val command =
            UpdateProfileCommand(
                userId = userId,
                firstName = "Alice",
                lastName = "New",
            )

        every { userRepository.findById(userId) } returns null

        assertThatThrownBy {
            handler.handle(command)
        }.isInstanceOf(UserNotFoundException::class.java)
    }
}
