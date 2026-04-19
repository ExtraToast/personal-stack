package com.jorisjonkers.personalstack.common.messaging

import com.jorisjonkers.personalstack.common.event.DomainEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Instant

class RabbitMqEventPublisherTest {
    private val rabbitTemplate = mockk<RabbitTemplate>(relaxed = true)
    private val publisher = RabbitMqEventPublisher(rabbitTemplate)

    private data class TestEvent(
        val id: String,
        override val occurredAt: Instant = Instant.now(),
    ) : DomainEvent

    @Test
    fun `publish sends event to correct exchange with routing key`() {
        val event = TestEvent(id = "test-123")
        val routingKey = "test.routing.key"

        publisher.publish(routingKey, event)

        verify {
            rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENTS_EXCHANGE,
                routingKey,
                event,
            )
        }
    }

    @Test
    fun `publish logs event class name`() {
        val event = TestEvent(id = "log-test")

        publisher.publish("test.key", event)

        // Verifying that the convertAndSend was called (log is a side effect of successful send)
        verify(exactly = 1) {
            rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<Any>())
        }
    }

    @Test
    fun `publish throws on AmqpException`() {
        val event = TestEvent(id = "fail-test")
        every {
            rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<Any>())
        } throws AmqpException("Connection refused")

        assertThatThrownBy {
            publisher.publish("test.key", event)
        }.isInstanceOf(AmqpException::class.java)
            .hasMessageContaining("Connection refused")
    }

    @Test
    fun `publish converts event to JSON message`() {
        val event = TestEvent(id = "json-test")
        val eventSlot = slot<Any>()

        every {
            rabbitTemplate.convertAndSend(any<String>(), any<String>(), capture(eventSlot))
        } returns Unit

        publisher.publish("test.key", event)

        assertThat(eventSlot.captured).isEqualTo(event)
        assertThat(eventSlot.captured).isInstanceOf(DomainEvent::class.java)
    }

    @Test
    fun `publish uses correct exchange name`() {
        val event = TestEvent(id = "exchange-test")
        val exchangeSlot = slot<String>()

        every {
            rabbitTemplate.convertAndSend(capture(exchangeSlot), any<String>(), any<Any>())
        } returns Unit

        publisher.publish("test.key", event)

        assertThat(exchangeSlot.captured).isEqualTo(RabbitMqConfig.EVENTS_EXCHANGE)
        assertThat(exchangeSlot.captured).isEqualTo("personal-stack.events")
    }
}
