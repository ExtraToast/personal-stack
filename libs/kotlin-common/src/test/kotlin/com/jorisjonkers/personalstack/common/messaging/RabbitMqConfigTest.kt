@file:Suppress("DEPRECATION")

package com.jorisjonkers.personalstack.common.messaging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter

class RabbitMqConfigTest {
    private val config = RabbitMqConfig()

    @Test
    fun `companion constants are correct`() {
        assertThat(RabbitMqConfig.EVENTS_EXCHANGE).isEqualTo("personal-stack.events")
        assertThat(RabbitMqConfig.DLX_EXCHANGE).isEqualTo("personal-stack.events.dlx")
        assertThat(RabbitMqConfig.USER_REGISTERED_ROUTING_KEY).isEqualTo("auth.user.registered")
        assertThat(RabbitMqConfig.USER_REGISTERED_QUEUE).isEqualTo("auth.user-registered")
        assertThat(RabbitMqConfig.USER_REGISTERED_DLQ).isEqualTo("auth.user-registered.dlq")
    }

    @Test
    fun `eventsExchange creates durable non-auto-delete exchange`() {
        val exchange = config.eventsExchange()
        assertThat(exchange.name).isEqualTo("personal-stack.events")
        assertThat(exchange.isDurable).isTrue()
        assertThat(exchange.isAutoDelete).isFalse()
    }

    @Test
    fun `dlxExchange creates durable non-auto-delete exchange`() {
        val exchange = config.dlxExchange()
        assertThat(exchange.name).isEqualTo("personal-stack.events.dlx")
        assertThat(exchange.isDurable).isTrue()
        assertThat(exchange.isAutoDelete).isFalse()
    }

    @Test
    fun `userRegisteredQueue is durable with DLX arguments`() {
        val queue = config.userRegisteredQueue()
        assertThat(queue.name).isEqualTo("auth.user-registered")
        assertThat(queue.isDurable).isTrue()
        assertThat(queue.arguments["x-dead-letter-exchange"]).isEqualTo("personal-stack.events.dlx")
        assertThat(queue.arguments["x-dead-letter-routing-key"]).isEqualTo("auth.user-registered.dlq")
    }

    @Test
    fun `userRegisteredDlq is durable`() {
        val queue = config.userRegisteredDlq()
        assertThat(queue.name).isEqualTo("auth.user-registered.dlq")
        assertThat(queue.isDurable).isTrue()
    }

    @Test
    fun `userRegisteredBinding binds queue to exchange with routing key`() {
        val queue = config.userRegisteredQueue()
        val exchange = config.eventsExchange()
        val binding = config.userRegisteredBinding(queue, exchange)
        assertThat(binding.exchange).isEqualTo("personal-stack.events")
        assertThat(binding.routingKey).isEqualTo("auth.user.registered")
        assertThat(binding.destination).isEqualTo("auth.user-registered")
    }

    @Test
    fun `messageConverter returns Jackson2JsonMessageConverter`() {
        val converter = config.messageConverter()
        assertThat(converter).isInstanceOf(Jackson2JsonMessageConverter::class.java)
    }
}
