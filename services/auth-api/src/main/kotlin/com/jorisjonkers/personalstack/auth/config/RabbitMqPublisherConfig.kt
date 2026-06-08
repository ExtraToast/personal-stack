package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.common.messaging.RabbitMqEventPublisher
import com.jorisjonkers.personalstack.common.messaging.RabbitMqMessagingProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class RabbitMqPublisherConfig {
    @Bean
    @ConditionalOnMissingBean(RabbitMqEventPublisher::class)
    fun rabbitMqEventPublisher(
        rabbitTemplate: RabbitTemplate,
        properties: RabbitMqMessagingProperties,
    ): RabbitMqEventPublisher = RabbitMqEventPublisher(rabbitTemplate, properties)
}
