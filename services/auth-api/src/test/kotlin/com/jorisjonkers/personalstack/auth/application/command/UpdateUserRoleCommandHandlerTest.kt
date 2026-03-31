package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.ServicePermission
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import com.jorisjonkers.personalstack.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class UpdateUserRoleCommandHandlerTest {
    private val userRepository = mockk<UserRepository>()
    private val handler = UpdateUserRoleCommandHandler(userRepository)

    private val userId = UserId(UUID.randomUUID())
    private val now = Instant.now()

    private val user =
        User(
            id = userId,
            username = "alice",
            email = "alice@example.com",
            firstName = "",
            lastName = "",
            role = Role.USER,
            emailConfirmed = true,
            totpEnabled = false,
            createdAt = now,
            updatedAt = now,
            servicePermissions = setOf(ServicePermission.GRAFANA),
        )

    @Test
    fun `handle updates user role to ADMIN`() {
        every { userRepository.findById(userId) } returns user
        every { userRepository.update(any()) } answers { firstArg() }

        val result = handler.handle(UpdateUserRoleCommand(userId, Role.ADMIN))

        assertThat(result.role).isEqualTo(Role.ADMIN)
        assertThat(result.servicePermissions).containsExactly(ServicePermission.GRAFANA)
        verify { userRepository.update(match { it.role == Role.ADMIN }) }
    }

    @Test
    fun `handle throws NotFoundException when user does not exist`() {
        every { userRepository.findById(userId) } returns null

        assertThatThrownBy {
            handler.handle(UpdateUserRoleCommand(userId, Role.ADMIN))
        }.isInstanceOf(NotFoundException::class.java)
    }
}
