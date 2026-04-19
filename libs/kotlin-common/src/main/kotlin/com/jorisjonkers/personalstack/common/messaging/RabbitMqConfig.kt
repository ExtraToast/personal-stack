@file:Suppress("DEPRECATION")

package com.jorisjonkers.personalstack.common.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class RabbitMqConfig {
    companion object {
        const val EVENTS_EXCHANGE = "personal-stack.events"
        const val DLX_EXCHANGE = "personal-stack.events.dlx"

        const val USER_REGISTERED_ROUTING_KEY = "auth.user.registered"

        const val USER_REGISTERED_QUEUE = "auth.user-registered"
        const val USER_REGISTERED_DLQ = "auth.user-registered.dlq"
    }

    @Bean
    open fun eventsExchange(): DirectExchange = DirectExchange(EVENTS_EXCHANGE, true, false)

    @Bean
    open fun dlxExchange(): DirectExchange = DirectExchange(DLX_EXCHANGE, true, false)

    @Bean
    open fun userRegisteredQueue(): Queue =
        QueueBuilder
            .durable(USER_REGISTERED_QUEUE)
            .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", USER_REGISTERED_DLQ)
            .build()

    @Bean
    open fun userRegisteredDlq(): Queue = QueueBuilder.durable(USER_REGISTERED_DLQ).build()

    @Bean
    open fun userRegisteredBinding(
        userRegisteredQueue: Queue,
        eventsExchange: DirectExchange,
    ): Binding = BindingBuilder.bind(userRegisteredQueue).to(eventsExchange).with(USER_REGISTERED_ROUTING_KEY)

    @Bean
    open fun messageConverter(): MessageConverter = Jackson2JsonMessageConverter()
}
