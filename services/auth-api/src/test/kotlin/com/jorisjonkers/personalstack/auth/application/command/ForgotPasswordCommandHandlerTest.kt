package com.jorisjonkers.personalstack.auth.application.command

import com.jorisjonkers.personalstack.auth.domain.event.PasswordResetRequestedEvent
import com.jorisjonkers.personalstack.auth.domain.model.PasswordResetToken
import com.jorisjonkers.personalstack.auth.domain.model.Role
import com.jorisjonkers.personalstack.auth.domain.model.User
import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.auth.domain.port.PasswordResetTokenRepository
import com.jorisjonkers.personalstack.auth.domain.port.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class ForgotPasswordCommandHandlerTest {
    private val userRepository = mockk<UserRepository>()
    private val passwordResetTokenRepository = mockk<PasswordResetTokenRepository>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

    private val handler =
        ForgotPasswordCommandHandler(
            userRepository,
            passwordResetTokenRepository,
            eventPublisher,
        )

    @Test
    fun `handle creates token and publishes event when user exists`() {
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
        val command = ForgotPasswordCommand(email = "alice@example.com")
        val tokenSlot = slot<PasswordResetToken>()
        val eventSlot = slot<PasswordResetRequestedEvent>()

        every { userRepository.findByEmail("alice@example.com") } returns user
        every { passwordResetTokenRepository.save(capture(tokenSlot)) } answers { tokenSlot.captured }
        every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

        handler.handle(command)

        assertThat(tokenSlot.captured.userId).isEqualTo(userId)
        assertThat(tokenSlot.captured.usedAt).isNull()
        assertThat(tokenSlot.captured.expiresAt).isAfter(Instant.now())
        assertThat(eventSlot.captured.userId).isEqualTo(userId)
        assertThat(eventSlot.captured.email).isEqualTo("alice@example.com")
        assertThat(eventSlot.captured.username).isEqualTo("alice")
        verify { passwordResetTokenRepository.save(any()) }
        verify { eventPublisher.publishEvent(any<PasswordResetRequestedEvent>()) }
    }

    @Test
    fun `handle returns silently when user not found`() {
        val command = ForgotPasswordCommand(email = "unknown@example.com")

        every { userRepository.findByEmail("unknown@example.com") } returns null

        handler.handle(command)

        verify(exactly = 0) { passwordResetTokenRepository.save(any()) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }
}
