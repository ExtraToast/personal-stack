package com.jorisjonkers.personalstack.auth.domain.event

import com.jorisjonkers.personalstack.auth.domain.model.UserId
import com.jorisjonkers.personalstack.common.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DomainEventTest {
    @Test
    fun `UserRegisteredEvent implements DomainEvent`() {
        val event =
            UserRegisteredEvent(
                userId = UserId(UUID.randomUUID()),
                username = "testuser",
                email = "test@example.com",
            )

        assertThat(event).isInstanceOf(DomainEvent::class.java)
    }

    @Test
    fun `UserRegisteredEvent has occurredAt`() {
        val before = Instant.now()
        val event =
            UserRegisteredEvent(
                userId = UserId(UUID.randomUUID()),
                username = "testuser",
                email = "test@example.com",
            )
        val after = Instant.now()

        assertThat(event.occurredAt).isBetween(before, after)
    }

    @Test
    fun `EmailConfirmationRequestedEvent implements DomainEvent`() {
        val event =
            EmailConfirmationRequestedEvent(
                userId = UserId(UUID.randomUUID()),
                username = "testuser",
                email = "test@example.com",
                confirmationToken = "token-123",
            )

        assertThat(event).isInstanceOf(DomainEvent::class.java)
        assertThat(event.confirmationToken).isEqualTo("token-123")
    }

    @Test
    fun `events are data classes with equals hashCode and copy`() {
        val userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        val timestamp = Instant.parse("2025-01-01T00:00:00Z")

        val event1 =
            UserRegisteredEvent(
                userId = userId,
                username = "testuser",
                email = "test@example.com",
                occurredAt = timestamp,
            )
        val event2 =
            UserRegisteredEvent(
                userId = userId,
                username = "testuser",
                email = "test@example.com",
                occurredAt = timestamp,
            )

        // equals
        assertThat(event1).isEqualTo(event2)
        // hashCode
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode())
        // copy
        val copied = event1.copy(username = "newuser")
        assertThat(copied.username).isEqualTo("newuser")
        assertThat(copied.email).isEqualTo("test@example.com")
        assertThat(copied.userId).isEqualTo(userId)
    }

    @Test
    fun `events store correct domain IDs`() {
        val userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))

        val registeredEvent =
            UserRegisteredEvent(
                userId = userId,
                username = "testuser",
                email = "test@example.com",
            )

        assertThat(registeredEvent.userId).isEqualTo(userId)
        assertThat(registeredEvent.userId.value)
            .isEqualTo(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))

        val confirmationEvent =
            EmailConfirmationRequestedEvent(
                userId = userId,
                username = "testuser",
                email = "test@example.com",
                confirmationToken = "token-abc",
            )

        assertThat(confirmationEvent.userId).isEqualTo(userId)
        assertThat(confirmationEvent.userId).isEqualTo(registeredEvent.userId)
    }
}
