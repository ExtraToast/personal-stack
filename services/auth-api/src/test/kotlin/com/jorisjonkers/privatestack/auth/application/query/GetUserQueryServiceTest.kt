package com.jorisjonkers.privatestack.auth.application.query

import com.jorisjonkers.privatestack.auth.domain.model.Role
import com.jorisjonkers.privatestack.auth.domain.model.User
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import com.jorisjonkers.privatestack.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GetUserQueryServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val service = GetUserQueryService(userRepository)

    @Test
    fun `findById returns user when found`() {
        val userId = UserId(UUID.randomUUID())
        val user =
            User(
                id = userId,
                username = "testuser",
                email = "test@example.com",
                role = Role.USER,
                totpEnabled = false,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            )
        every { userRepository.findById(userId) } returns user

        val result = service.findById(userId)

        assertThat(result.username).isEqualTo("testuser")
    }

    @Test
    fun `findById throws NotFoundException when not found`() {
        val userId = UserId(UUID.randomUUID())
        every { userRepository.findById(userId) } returns null

        assertThatThrownBy { service.findById(userId) }
            .isInstanceOf(NotFoundException::class.java)
    }
}
