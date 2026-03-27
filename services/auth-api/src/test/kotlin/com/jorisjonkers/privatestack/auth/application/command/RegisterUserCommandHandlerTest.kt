package com.jorisjonkers.privatestack.auth.application.command

import com.jorisjonkers.privatestack.auth.domain.exception.DuplicateEmailException
import com.jorisjonkers.privatestack.auth.domain.exception.DuplicateUsernameException
import com.jorisjonkers.privatestack.auth.domain.model.Role
import com.jorisjonkers.privatestack.auth.domain.model.User
import com.jorisjonkers.privatestack.auth.domain.model.UserId
import com.jorisjonkers.privatestack.auth.domain.port.PasswordEncoder
import com.jorisjonkers.privatestack.auth.domain.port.UserRepository
import com.jorisjonkers.privatestack.common.messaging.RabbitMqEventPublisher
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class RegisterUserCommandHandlerTest {
    private val userRepository = mockk<UserRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val rabbitMqEventPublisher = mockk<RabbitMqEventPublisher>(relaxed = true)

    private val handler =
        RegisterUserCommandHandler(
            userRepository,
            passwordEncoder,
            eventPublisher,
            rabbitMqEventPublisher,
        )

    @Test
    fun `handle registers a new user successfully`() {
        val command =
            RegisterUserCommand(
                username = "alice",
                email = "alice@example.com",
                password = "secret123",
            )
        val userSlot = slot<User>()
        val hashSlot = slot<String>()

        every { userRepository.existsByUsername("alice") } returns false
        every { userRepository.existsByEmail("alice@example.com") } returns false
        every { passwordEncoder.encode("secret123") } returns "\$2a\$10\$hashed"
        every { userRepository.create(capture(userSlot), capture(hashSlot)) } answers {
            userSlot.captured
        }

        handler.handle(command)

        assertThat(userSlot.captured.username).isEqualTo("alice")
        assertThat(userSlot.captured.email).isEqualTo("alice@example.com")
        assertThat(userSlot.captured.role).isEqualTo(Role.USER)
        assertThat(userSlot.captured.totpEnabled).isFalse()
        assertThat(hashSlot.captured).isEqualTo("\$2a\$10\$hashed")
        verify { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `handle throws DuplicateUsernameException when username already taken`() {
        every { userRepository.existsByUsername("alice") } returns true

        assertThatThrownBy {
            handler.handle(RegisterUserCommand("alice", "alice@example.com", "pass"))
        }.isInstanceOf(DuplicateUsernameException::class.java)
    }

    @Test
    fun `handle throws DuplicateEmailException when email already registered`() {
        every { userRepository.existsByUsername("alice") } returns false
        every { userRepository.existsByEmail("alice@example.com") } returns true

        assertThatThrownBy {
            handler.handle(RegisterUserCommand("alice", "alice@example.com", "pass"))
        }.isInstanceOf(DuplicateEmailException::class.java)
    }

    private fun buildUser(
        username: String,
        email: String,
    ): User {
        val now = Instant.now()
        return User(
            id = UserId(UUID.randomUUID()),
            username = username,
            email = email,
            role = Role.USER,
            totpEnabled = false,
            createdAt = now,
            updatedAt = now,
        )
    }
}
