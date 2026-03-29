package com.jorisjonkers.personalstack.common.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jorisjonkers.personalstack.common.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EventSerializationTest {
    private val objectMapper: ObjectMapper =
        jacksonObjectMapper().registerModule(JavaTimeModule())

    // Lightweight test event mirrors the structure of UserRegisteredEvent
    private data class UserRegisteredEventTest(
        val userId: UUID,
        val username: String,
        val email: String,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent

    // Lightweight test event mirrors the structure of ConversationStartedEvent
    private data class ConversationStartedEventTest(
        val conversationId: UUID,
        val userId: UUID,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent

    @Test
    fun `UserRegisteredEvent serializes to JSON correctly`() {
        val event =
            UserRegisteredEventTest(
                userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                username = "testuser",
                email = "test@example.com",
            )

        val json = objectMapper.writeValueAsString(event)

        assertThat(json).contains("\"userId\"")
        assertThat(json).contains("\"username\":\"testuser\"")
        assertThat(json).contains("\"email\":\"test@example.com\"")
        assertThat(json).contains("\"occurredAt\"")
    }

    @Test
    fun `ConversationStartedEvent serializes to JSON correctly`() {
        val conversationId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val event =
            ConversationStartedEventTest(
                conversationId = conversationId,
                userId = userId,
            )

        val json = objectMapper.writeValueAsString(event)

        assertThat(json).contains("\"conversationId\"")
        assertThat(json).contains("\"userId\"")
        assertThat(json).contains(conversationId.toString())
        assertThat(json).contains(userId.toString())
    }

    @Test
    fun `events include occurredAt timestamp`() {
        val before = Instant.now()
        val event =
            UserRegisteredEventTest(
                userId = UUID.randomUUID(),
                username = "timestamp-test",
                email = "ts@example.com",
            )
        val after = Instant.now()

        assertThat(event.occurredAt).isBetween(before, after)

        val json = objectMapper.writeValueAsString(event)
        assertThat(json).contains("\"occurredAt\"")
    }

    @Test
    fun `events can be deserialized from JSON`() {
        val original =
            UserRegisteredEventTest(
                userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                username = "deserialize-test",
                email = "deser@example.com",
                occurredAt = Instant.parse("2025-01-01T00:00:00Z"),
            )

        val json = objectMapper.writeValueAsString(original)
        val deserialized = objectMapper.readValue<UserRegisteredEventTest>(json)

        assertThat(deserialized).isEqualTo(original)
        assertThat(deserialized.userId).isEqualTo(original.userId)
        assertThat(deserialized.username).isEqualTo(original.username)
        assertThat(deserialized.email).isEqualTo(original.email)
        assertThat(deserialized.occurredAt).isEqualTo(original.occurredAt)
    }

    @Test
    fun `serialized event preserves all fields`() {
        val userId = UUID.randomUUID()
        val conversationId = UUID.randomUUID()
        val timestamp = Instant.parse("2025-06-15T12:30:00Z")

        val event =
            ConversationStartedEventTest(
                conversationId = conversationId,
                userId = userId,
                occurredAt = timestamp,
            )

        val json = objectMapper.writeValueAsString(event)
        val deserialized = objectMapper.readValue<ConversationStartedEventTest>(json)

        assertThat(deserialized.conversationId).isEqualTo(conversationId)
        assertThat(deserialized.userId).isEqualTo(userId)
        assertThat(deserialized.occurredAt).isEqualTo(timestamp)
    }
}
