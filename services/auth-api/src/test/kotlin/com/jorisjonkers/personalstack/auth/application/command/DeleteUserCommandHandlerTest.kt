package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DeleteUserCommandHandlerTest {
    private val userRepository = mockk<UserRepository>()
    private val handler = DeleteUserCommandHandler(userRepository)

    private val userId = UserId(UUID.randomUUID())

    private val user =
        User(
            id = userId,
            username = "alice",
            email = "alice@example.com",
            role = Role.USER,
            emailConfirmed = true,
            totpEnabled = false,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

    @Test
    fun `handle deletes user by id`() {
        every { userRepository.findById(userId) } returns user
        every { userRepository.deleteById(userId) } returns Unit

        handler.handle(DeleteUserCommand(userId))

        verify { userRepository.deleteById(userId) }
    }

    @Test
    fun `handle throws NotFoundException when user does not exist`() {
        every { userRepository.findById(userId) } returns null

        assertThatThrownBy {
            handler.handle(DeleteUserCommand(userId))
        }.isInstanceOf(NotFoundException::class.java)
    }
}
